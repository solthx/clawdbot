package com.openclaw.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Tiny HTTP shell for learning:
 * POST /agent?sessionKey=...&body=...&lane=...
 * GET /agent/wait?runId=...&timeoutMs=...
 */
public final class GatewayHttpServer {
  private final GatewayCore gateway;
  private final HttpServer server;

  public GatewayHttpServer(GatewayCore gateway, int port) throws IOException {
    this.gateway = gateway;
    this.server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/agent", this::handleAgent);
    server.createContext("/agent/wait", this::handleWait);
  }

  public void start() {
    server.start();
  }

  public void stop() {
    server.stop(0);
  }

  private void handleAgent(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      respond(exchange, 405, "method not allowed");
      return;
    }
    var query = parseQuery(exchange.getRequestURI().getRawQuery());
    var sessionKey = query.getOrDefault("sessionKey", "main");
    var body = query.getOrDefault("body", "");
    var lane = query.get("lane");
    var idempotencyKey = query.get("idempotencyKey");

    try {
      var accepted = gateway.accept(new MessageRequest(sessionKey, body, "internal", null, null, idempotencyKey), lane);
      respond(exchange, 200, toJson(accepted.asMap()));
    } catch (IllegalArgumentException err) {
      respond(exchange, 400, err.getMessage());
    }
  }

  private void handleWait(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      respond(exchange, 405, "method not allowed");
      return;
    }
    var query = parseQuery(exchange.getRequestURI().getRawQuery());
    var runId = query.get("runId");
    if (runId == null || runId.isBlank()) {
      respond(exchange, 400, "runId is required");
      return;
    }
    var timeoutMs = Long.parseLong(query.getOrDefault("timeoutMs", "30000"));
    var snapshot = gateway.waitForRun(runId, Duration.ofMillis(timeoutMs)).join();
    respond(exchange, 200, toJson(Map.of(
        "runId", snapshot.runId(),
        "status", snapshot.status(),
        "startedAt", String.valueOf(snapshot.startedAt()),
        "endedAt", String.valueOf(snapshot.endedAt()),
        "error", String.valueOf(snapshot.error())
    )));
  }

  private static Map<String, String> parseQuery(String rawQuery) {
    var result = new HashMap<String, String>();
    if (rawQuery == null || rawQuery.isBlank()) {
      return result;
    }
    for (var part : rawQuery.split("&")) {
      var pair = part.split("=", 2);
      var key = java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
      var value = pair.length > 1 ? java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
      result.put(key, value);
    }
    return result;
  }

  private static String toJson(Map<String, Object> map) {
    var sb = new StringBuilder("{");
    var first = true;
    for (var entry : map.entrySet()) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      sb.append('"').append(entry.getKey()).append('"').append(':');
      sb.append('"').append(String.valueOf(entry.getValue()).replace("\"", "\\\"")).append('"');
    }
    sb.append('}');
    return sb.toString();
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }
}
