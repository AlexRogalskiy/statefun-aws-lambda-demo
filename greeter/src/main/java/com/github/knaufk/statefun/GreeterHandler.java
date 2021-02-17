package com.github.knaufk.statefun;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.*;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.undertow.util.Headers;
import org.apache.flink.statefun.sdk.java.StatefulFunctionSpec;
import org.apache.flink.statefun.sdk.java.StatefulFunctions;
import org.apache.flink.statefun.sdk.java.handler.RequestReplyHandler;
import org.apache.flink.statefun.sdk.java.slice.Slice;
import org.apache.flink.statefun.sdk.java.slice.Slices;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class GreeterHandler implements RequestStreamHandler {

  private final RequestReplyHandler handler;

  public GreeterHandler() {
    StatefulFunctionSpec spec =
        StatefulFunctionSpec.builder(GreeterFn.TYPE)
            .withValueSpec(GreeterFn.SEEN)
            .withSupplier(GreeterFn::new)
            .build();

    // obtain a request-reply handler based on the spec above
    StatefulFunctions functions = new StatefulFunctions();
    functions.withStatefulFunction(spec);
    handler = functions.requestReplyHandler();
  }

  @Override
  public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) {
    try {
      Slice requestSlice = unwrapSliceFromJsonRequest(inputStream);
      CompletableFuture<Slice> handle = handler.handle(requestSlice);
      Slice responseSlice = handle.get();
      wrapSliceInJsonResponse(outputStream, responseSlice);
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  private Slice unwrapSliceFromJsonRequest(InputStream inputStream)
      throws IOException, ParseException {
    JSONParser parser = new JSONParser();
    try(BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      JSONObject event = (JSONObject) parser.parse(reader);
      System.out.println(event);
      String base64Input = (String) event.get("body");
      Slice slice = Slices.wrap(Base64.getDecoder().decode(base64Input));
      return slice;
    }
  }

  private void wrapSliceInJsonResponse(OutputStream outputStream, Slice s) {

    String body = Base64.getEncoder().encodeToString(s.toByteArray());

    JSONObject responseJson = new JSONObject();
    JSONObject headerJson = new JSONObject();
    headerJson.put(Headers.CONTENT_TYPE, "application/octet-stream");
    JSONObject multiValueHeaders = new JSONObject();

    responseJson.put("statusCode", 200);
    responseJson.put("isBase64Encoded", true);
    responseJson.put("body", body);
    responseJson.put("headers", headerJson);
    responseJson.put("multiValueHeaders", multiValueHeaders);

    try (OutputStreamWriter writer  = new OutputStreamWriter(outputStream, "UTF-8")) {
      String str = responseJson.toString();
      System.out.println(str);
      writer.write(str);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}