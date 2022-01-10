// SamlDecoderTest.java
//
// Test code for the AWS V4 signature  callout for Apigee. Uses TestNG.
// For full details see the Readme accompanying this source file.
//
// Copyright (c) 2016 Apigee Corp, 2017-2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.google.apigee.callouts;

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.message.Message;
import com.apigee.flow.message.MessageContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import mockit.Mock;
import mockit.MockUp;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class SamlDecoderTest {
  private static final String testDataDir = "src/test/resources";
  private static final boolean verbose = true;
  MessageContext msgCtxt;
  Message message;
  ExecutionContext exeCtxt;

  @BeforeMethod()
  public void testSetup1() {

    msgCtxt =
        new MockUp<MessageContext>() {
          private Map<String, Object> variables;

          public void $init() {
            getVariables();
          }

          private Map<String, Object> getVariables() {
            if (variables == null) {
              variables = new HashMap<String, Object>();
            }
            return variables;
          }

          @Mock()
          public Object getVariable(final String name) {
            return getVariables().get(name);
          }

          @Mock()
          public boolean setVariable(final String name, final Object value) {
            if (verbose)
              System.out.printf(
                  "setVariable(%s) <= %s\n", name, (value != null) ? value : "(null)");
            getVariables().put(name, value);
            return true;
          }

          @Mock()
          public boolean removeVariable(final String name) {
            if (verbose) System.out.printf("removeVariable(%s)\n", name);
            if (getVariables().containsKey(name)) {
              variables.remove(name);
            }
            return true;
          }
        }.getMockInstance();

    exeCtxt = new MockUp<ExecutionContext>() {}.getMockInstance();

    message =
        new MockUp<Message>() {
          private Map<String, Object> variables;
          private Map<String, Object> headers;
          private Map<String, Object> qparams;
          private String content;

          public void $init() {
            getVariables();
          }

          private Map<String, Object> getVariables() {
            if (variables == null) {
              variables = new HashMap<String, Object>();
            }
            return variables;
          }

          private Map<String, Object> getHeaders() {
            if (headers == null) {
              headers = new HashMap<String, Object>();
            }
            return headers;
          }

          private Map<String, Object> getQparams() {
            if (qparams == null) {
              qparams = new HashMap<String, Object>();
            }
            return qparams;
          }

          @Mock()
          public String getContent() {
            return this.content;
          }

          @Mock()
          public void setContent(String content) {
            this.content = content;
          }

          @Mock()
          public Object getVariable(final String name) {
            return getVariables().get(name);
          }

          @Mock()
          public boolean setVariable(final String name, final Object value) {
            getVariables().put(name, value);
            return true;
          }

          @Mock()
          public boolean removeVariable(final String name) {
            if (getVariables().containsKey(name)) {
              variables.remove(name);
            }
            return true;
          }

          @Mock()
          public String getHeader(final String name) {
            List<String> headerList = getHeaders(name);
            return (headerList != null) ? headerList.get(0) : null;
          }

          @Mock()
          public List<String> getHeaders(final String name) {
            String lowerName = name.toLowerCase();
            if (getHeaders().containsKey(lowerName)) {
              @SuppressWarnings("unchecked")
              List<String> list = (List<String>) getHeaders().get(lowerName);
              return list;
            }
            return null;
          }

          @Mock()
          public boolean setHeader(final String name, final Object value) {
            String lowerName = name.toLowerCase();
            if (verbose) {
              System.out.printf(
                  "setHeader(%s) <= %s\n", lowerName, (value != null) ? value : "(null)");
            }
            if (getHeaders().containsKey(lowerName)) {
              if (!lowerName.equals("host")) {
                @SuppressWarnings("unchecked")
                List<String> values = (List<String>) getHeaders().get(lowerName);
                values.add(value.toString());
              }
            } else {
              List<String> values = new ArrayList<String>();
              values.add(value.toString());
              getHeaders().put(lowerName, values);
            }
            return true;
          }

          @Mock()
          public boolean removeHeader(final String name) {
            String lowerName = name.toLowerCase();
            if (verbose) {
              System.out.printf("removeHeader(%s)\n", lowerName);
            }
            if (getHeaders().containsKey(lowerName)) {
              getHeaders().remove(lowerName);
            }
            return true;
          }

          @Mock()
          public Set<String> getHeaderNames() {
            return getHeaders().entrySet().stream()
                .map(e -> e.getKey())
                .collect(Collectors.toSet());
          }

          @Mock()
          public Set<String> getQueryParamNames() {
            return getQparams().entrySet().stream()
                .map(e -> e.getKey())
                .collect(Collectors.toSet());
          }

          @Mock()
          public String getQueryParam(final String name) {
            List<String> paramList = getQueryParams(name);
            return (paramList!=null) ? paramList.get(0) : null;
          }

          @Mock()
          public boolean setQueryParam(final String name, final Object value) {
            if (verbose) {
              System.out.printf(
                  "setQueryParam(%s) <= %s\n", name, (value != null) ? value : "(null)");
            }
            if (getQparams().containsKey(name)) {
              @SuppressWarnings("unchecked")
              List<String> values = (List<String>) getQparams().get(name);
              values.add(value.toString());
            } else {
              List<String> values = new ArrayList<String>();
              values.add(value.toString());
              getQparams().put(name, values);
            }
            return true;
          }

          @Mock()
          public List<String> getQueryParams(final String name) {
            if (getQparams().containsKey(name)) {
              @SuppressWarnings("unchecked")
              List<String> list = (List<String>) getQparams().get(name);
              return list;
            }
            return null;
          }
        }.getMockInstance();

    System.out.printf("=============================================\n");
  }

  private static Document docFromStream(InputStream inputStream)
      throws IOException, ParserConfigurationException, SAXException {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    Document doc = dbf.newDocumentBuilder().parse(inputStream);
    return doc;
  }

  static String getFileContents(String filename) throws IOException {
    File dataDir = new File(testDataDir);
    if (!dataDir.exists()) {
      throw new IllegalStateException("no test data directory.");
    }
    return readAll(Paths.get(testDataDir, filename));
  }

  static String readAll(Path path) throws IOException {
    return new String(Files.readAllBytes(path));
  }


  @Test
  public void sample1() throws Exception {
    msgCtxt.setVariable("source", message);
    message.setContent(getFileContents("encoded-saml-assertion-1.txt"));

    Properties props = new Properties();
    props.setProperty("input", "source");
    props.setProperty("output", "output");
    props.setProperty("inflate", "false");

    SamlDecoder callout = new SamlDecoder(props);

    // execute and retrieve output
    ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
    ExecutionResult expectedResult = ExecutionResult.SUCCESS;

    // check result and output
    Assert.assertEquals(actualResult, expectedResult);
    Assert.assertNull(msgCtxt.getVariable("decoder_error"));
    String output = (String) msgCtxt.getVariable("output");
    Assert.assertNotNull(output);
    //System.out.printf("%s\n", output);

    Document doc = docFromStream(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));

    // samlp "urn:oasis:names:tc:SAML:2.0:protocol"
    // saml "urn:oasis:names:tc:SAML:2.0:assertion"
    NodeList nl = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Assertion");
    Assert.assertEquals(nl.getLength(), 1, "Assertion element");

  }

  @Test
  public void sample2() throws Exception {
    msgCtxt.setVariable("source", message);
    message.setContent(getFileContents("encoded-saml-assertion-2.txt"));

    Properties props = new Properties();
    props.setProperty("input", "source");
    props.setProperty("output", "output");
    //props.setProperty("inflate", "false");

    SamlDecoder callout = new SamlDecoder(props);

    // execute and retrieve output
    ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
    ExecutionResult expectedResult = ExecutionResult.SUCCESS;

    // check result and output
    Assert.assertEquals(actualResult, expectedResult);
    Assert.assertNull(msgCtxt.getVariable("decoder_error"));
    String output = (String) msgCtxt.getVariable("output");
    Assert.assertNotNull(output);
    // System.out.printf("%s\n", output);

    Document doc = docFromStream(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));

    // samlp "urn:oasis:names:tc:SAML:2.0:protocol"
    // saml "urn:oasis:names:tc:SAML:2.0:assertion"
    NodeList nl = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Assertion");
    Assert.assertEquals(nl.getLength(), 1, "Assertion element");

  }

}
