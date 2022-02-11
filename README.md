# Java SAML Decoder

This directory contains the Java source code and pom.xml file required to
compile a simple Java callout for Apigee. The callout is very simple: it
decodes an encoded SAML Assertion.

This policy does not Validate the signature on the decoded assertion. For that you can use the builtin policy, ValidateSAMLAssertion. 

## Why is this callout necessary?

The Apigee builtin policy
[ValidateSAMLAssertion](https://cloud.google.com/apigee/docs/api-platform/reference/policies/saml-assertion-policy#usagenotes-validatesamlassertion)
will verify the SAML signature based on certificates that you have configured in the Apigee TrustStore.

[ExtractVariables](https://cloud.google.com/apigee/docs/api-platform/reference/policies/extract-variables-policy)
can allow you to extract from that XML document any attributes of interest, like the user attribute,
into context variables which you can then reference in subsequent policies.

The only obstacle is that `ValidateSAMLAssertion` requires the SAML to be
presented in a non-encoded XML form. If you have a base64-encoded, compressed
version of a SAML assertion, `ValidateSAMLAssertion` will not be able to handle
it.

But some systems produce SAML assertions in encoded form. The encoded assertion
is produced by a multi-step process like this:

1. generate the XML form of the signed SAML assertion
2. (optionally) compress the XML text into a bytestream
3. base64-encode that bytestream into a String
4. url-encode that resulting String

Often this SAML Assertion is encoded for use as a header or form parameter in an
HTTP request. It's often named `SAMLResponse`.

This callout provides a way to reverse steps 4, 3, and 2 of that process, to produce
an XML version of the assertion that `ValidateSAMLAssertion` can handle.

## LICENSE

This material is Copyright © 2017-2022 Google LLC.
and is licensed under the Apache 2.0 license. See the [LICENSE](LICENSE) file.

## Disclaimer

This example is not an official Google product, nor is it part of an official Google product.

## Building

You do not need to build this callout in order to use it.  But if you wish to
build it, you can do so.

1. unpack (if you can read this, you've already done that).

2. configure the build on your machine by loading the Apigee jars into your local cache
   ```
   ./buildsetup.sh
   ```

2. Build with maven.
   ```
   mvn clean package
   ```

3. if you edit proxy bundles offline, copy the resulting jar file, available in
   target/apigee-java-callout-samldecoder-20220110.jar to your
   apiproxy/resources/java directory.  If you don't edit proxy bundles offline,
   upload the jar file into the API Proxy via the Apigee API Proxy Editor .


## Usage Notes

If you want to decode and then validate a SAML Assertion, you will need to first
use this callout, and then use the ValidateSAMLAssertion policy. But, you will
also need an AssignMessage, as a complement to the callout. Here's why:

The ValidateSAMLAssertion policy requires an input of type `Message`.  This
callout can emit the XML string value into either,

* a context variable of `String` type
* a context variable of `Message` type

The callout can create a context variable of String type. Unfortunately the
callout _is not able to create a variable of type `Message`_ on its own.

Therefore one way or the other, you need to create a `Message`, which you can do
with an AssignMessage policy. You can attach the AssignMessage policy into the
flow either before or after the callout runs.


### Option 1: Callout, then AssignMessage

Configure your API proxy to execute the Java callout first. Configure it to emit
the output to a String variable. Like this:

```xml
<JavaCallout name='Java-SamlDecode'>
  <Properties>
    <Property name='input'>inbound_encoded_saml_token</Property>
    <!-- this will be a String that the policy creates -->
    <Property name='output'>outbound_decoded_saml_assertion</Property>
  </Properties>
  <ClassName>com.google.apigee.callouts.SamlDecoder</ClassName>
  <ResourceURL>java://apigee-java-callout-samldecoder-20220110.jar</ResourceURL>
</JavaCallout>
```

And then follow it with AssignMessage which uses that String variable to
populate the content of the payload. Like this:

```xml
<AssignMessage name='AM-ContrivedMessage-1'>
  <AssignTo createNew='true' transport='http' type='request'>contrivedMessage</AssignTo>
  <Set>
    <!-- fill the message content with the output of the prior policy -->
    <Payload contentType='text/xml'>{outbound_decoded_saml_assertion}</Payload>
    <Verb>POST</Verb>
  </Set>
</AssignMessage>
```


### Option 2: AssignMessage, then callout

Attach the AssignMessage policy before the Java callout, and configure it to
create an "empty" message variable:

```xml
<AssignMessage name='AM-ContrivedMessage-1'>
  <!-- this specifies the name of a new context variable of Message type -->
  <AssignTo createNew='true' transport='http' type='request'>contrivedMessage</AssignTo>
  <Set>
    <!-- this message content will be replaced later -->
    <Payload contentType='text/xml'><![CDATA[<root/>]]></Payload>
    <Verb>POST</Verb>
  </Set>
</AssignMessage>
```

And then follow that with the SAML Decode Java callout:

```xml
<JavaCallout name='Java-SamlDecode'>
  <Properties>
    <Property name='input'>name-of-variable-containing-encoded-saml-token</Property>
    <!-- specify the message created by the prior AssignMessage -->
    <Property name='output'>contrivedMessage</Property>
  </Properties>
  <ClassName>com.google.apigee.callouts.SamlDecoder</ClassName>
  <ResourceURL>java://apigee-java-callout-samldecoder-20220110.jar</ResourceURL>
</JavaCallout>
```

### Configuration of the Callout

The `input` and `output` properties are required.

The `input` variable can be a string or a message. The callout will do the right
thing for either. The `output` variable can be the name of an existing
message. In that case, the callout will set the content of the message to
contain the decoded XML. Otherwise, the callout will create a new context
variable, or set the value of the existing context variable, to a String value
of the decoded XML.


There are two optional properties:

| name       | description        |
| ---------- | ------------------ |
| inflate    | tells the callout whether to try to Inflate the decoded bytestream. Default: `true`. Set this property explicitly to `false` to turn this behavior off. |
| url-decode | tells the callout whether to try to url-decode the string, before base64-decoding. Default: `false`  |


For example, if the inbound SAML assertion is encoded but not deflated, and you use the default inflate behavior, yu will see these context variables in output:

| name                | description              |
| ------------------- | ------------------------ |
| decoder\_error      | invalid code lengths set |
| decoder\_stacktrace | java.util.zip.DataFormatException: invalid code lengths set at java.util.zip.Inflater.inflateBytes(Native Method) at ... |

To avoid that and to allow the decoder to succeed, configure the policy like this:
```xml
<JavaCallout name='Java-SamlDecode-No-Inflate'>
  <Properties>
    <Property name='input'>name-of-variable-containing-encoded-saml-token</Property>
    <Property name='inflate'>false</Property>
    <!-- specify the message created by a prior AssignMessage -->
    <Property name='output'>contrivedMessage</Property>
  </Properties>
  <ClassName>com.google.apigee.callouts.SamlDecoder</ClassName>
  <ResourceURL>java://apigee-java-callout-samldecoder-20220110.jar</ResourceURL>
</JavaCallout>
```




Further notes:

* If the encoded SAML assertion is contained within the normala request query parameter, you can
  use `request.queryparam.SAMLResponse` as the value for the `input` property.

## Bugs

- The tests are pretty thin.
