# Java SAML Decoder

This directory contains the Java source code and pom.xml file required to
compile a simple Java callout for Apigee. The callout is very simple: it decodes an encoded SAML Assertion.

## LICENSE

This material is copyright 2017-2022 Google LLC.
and is licensed under the Apache 2.0 license. See the [LICENSE](LICENSE) file.

## Disclaimer

This example is not an official Google product, nor is it part of an official Google product.

## Building:

You do not need to build this callout in order to use it.  But if you wish to build it, you can do so.

1. unpack (if you can read this, you've already done that).

2. configure the build on your machine by loading the Apigee jars into your local cache
  ```
  ./buildsetup.sh
  ```

2. Build with maven.
  ```
  mvn clean package
  ```

3. if you edit proxy bundles offline, copy the resulting jar file, available in  target/apigee-java-callout-samldecoder-20220105.jar to your apiproxy/resources/java directory.  If you don't edit proxy bundles offline, upload the jar file into the API Proxy via the Apigee API Proxy Editor .

4. include an XML file for the Java callout policy in your
   apiproxy/resources/policies directory. It should look
   like this:
   ```xml
    <JavaCallout name='Java-SamlDecode'>
      <Properties>
        <Property name='input'>variable-name-containing-encoded-saml-token</Property>
        <Property name='output'>variable-name-to-receive-decoded-saml-token</Property>
      </Properties>
      <ClassName>com.google.apigee.callouts.SamlDecoder</ClassName>
      <ResourceURL>java://apigee-java-callout-samldecoder-20220105.jar</ResourceURL>
    </JavaCallout>
   ```

The `input` variable can be a string or a message.  The callout will do the right thing for either.
The `output` variable can be the name of an existing message. In that case, the callout will set the content of the message to contain the decoded XML.  Otherwise, the callout will set the string value of the XML directly into the output variable.

5. Deploy and test as usual.


## Usage Notes

* If the encoded SAML assertion is contained within a request header, you can use `request.header.HEADERNAME` as the value for the `input` property.

* If you need a message to contain the output, you can contrive one with an AssignMessage policy like this:
  ```
   <AssignMessage name='AM-ContrivedMessage-1'>
     <AssignTo createNew='true' transport='http' type='request'>contrivedMessage</AssignTo>
     <Set>
       <!-- this message content will be replaced later -->
       <Payload contentType='text/xml'><![CDATA[<root/>]]></Payload>
       <Verb>POST</Verb>
     </Set>
   </AssignMessage>
  ```
  ... and then use `contrivedMessage` as the value for the `output` property.

## Bugs

- The tests are pretty thin.
