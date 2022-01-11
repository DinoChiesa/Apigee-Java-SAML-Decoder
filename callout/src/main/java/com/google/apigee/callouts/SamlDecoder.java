// Copyright © 2022 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
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
import com.apigee.flow.execution.spi.Execution;
import com.apigee.flow.message.Message;
import com.apigee.flow.message.MessageContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class SamlDecoder implements Execution {
  private static final String varprefix = "decoder_";
  private static final String variableReferencePatternString =
      "(.*?)\\{([^\\{\\} :][^\\{\\} ]*?)\\}(.*?)";
  private static final Pattern variableReferencePattern =
      Pattern.compile(variableReferencePatternString);

  private static String varName(String s) {
    return varprefix + s;
  }

  private Map properties; // read-only

  public SamlDecoder(Map properties) {
    this.properties = properties;
  }

  private String getInput(MessageContext msgCtxt) throws Exception {
    return getSimpleRequiredProperty("input", msgCtxt);
  }

  private String getOutput(MessageContext msgCtxt) throws Exception {
    return getSimpleRequiredProperty("output", msgCtxt);
  }

  private boolean getInflate(MessageContext msgCtxt) throws Exception {
    return _getBooleanProperty(msgCtxt, "inflate", true);
  }

  private boolean getUrlDecode(MessageContext msgCtxt) throws Exception {
    return _getBooleanProperty(msgCtxt, "url-decode", false);
  }

  protected boolean _getBooleanProperty(
      MessageContext msgCtxt, String propName, boolean defaultValue) throws Exception {
    String flag = (String) this.properties.get(propName);
    if (flag != null) flag = flag.trim();
    if (flag == null || flag.equals("")) {
      return defaultValue;
    }
    flag = resolveVariableReferences(flag, msgCtxt);
    if (flag == null || flag.equals("")) {
      return defaultValue;
    }
    return flag.equalsIgnoreCase("true");
  }

  private String getSimpleRequiredProperty(String propName, MessageContext msgCtxt)
      throws Exception {
    String value = (String) this.properties.get(propName);
    if (value == null) {
      throw new IllegalStateException(propName + " resolves to an empty string.");
    }
    value = value.trim();
    if (value.equals("")) {
      throw new IllegalStateException(propName + " resolves to an empty string.");
    }
    return value;
  }

  private String resolveVariableReferences(String spec, MessageContext msgCtxt) {
    Matcher matcher = variableReferencePattern.matcher(spec);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(sb, "");
      sb.append(matcher.group(1));
      String ref = matcher.group(2);
      String[] parts = ref.split(":", 2);
      Object v = msgCtxt.getVariable(parts[0]);
      if (v != null) {
        sb.append((String) v);
      } else if (parts.length > 1) {
        sb.append(parts[1]);
      }
      sb.append(matcher.group(3));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  public static byte[] decompress(byte[] data) throws IOException, DataFormatException {
    Inflater inflater = new Inflater(true);
    inflater.setInput(data);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    while (!inflater.finished()) {
      int count = inflater.inflate(buffer);
      outputStream.write(buffer, 0, count);
    }
    outputStream.close();
    byte[] output = outputStream.toByteArray();
    return output;
  }

  protected static String getStackTraceAsString(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }

  public ExecutionResult execute(final MessageContext msgCtxt, final ExecutionContext execContext) {
    try {
      String inputVariableName = getInput(msgCtxt);
      String outputVariableName = getOutput(msgCtxt);
      Object input = msgCtxt.getVariable(inputVariableName);
      if (input == null) {
        throw new IllegalStateException("input is missing.");
      }

      String encodedString =
          (input instanceof Message) ? ((Message) input).getContent() : (String) input;

      if (getUrlDecode(msgCtxt)) {
        encodedString = java.net.URLDecoder.decode(encodedString, StandardCharsets.UTF_8.name());
      }

      encodedString = encodedString.trim().replaceAll("\\r\\n|\\r|\\n", "");
      // base64-Decode the String into bytes
      byte[] decoded = Base64.getDecoder().decode(encodedString);
      // Decompress the bytes
      byte[] decompressed = (getInflate(msgCtxt)) ? decompress(decoded) : decoded;

      // Decode the bytes into a String
      String samlString = new String(decompressed, 0, decompressed.length, "UTF-8");
      Object output = (String) msgCtxt.getVariable(outputVariableName);
      if (output == null || !(output instanceof Message)) {
        msgCtxt.setVariable(outputVariableName, samlString);
      } else {
        Message message = (Message) output;
        message.setContent(samlString);
      }
      return ExecutionResult.SUCCESS;
    } catch (java.lang.Exception exc1) {
      msgCtxt.setVariable(varName("error"), exc1.getMessage());
      msgCtxt.setVariable(varName("stacktrace"), getStackTraceAsString(exc1));
      return ExecutionResult.ABORT;
    }
  }
}
