package com.openclaw.gateway;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Demo engine that emits lifecycle/tool/assistant events.
 */
public final class MockAgentEngine implements AgentEngine {
  @Override
  public CompletableFuture<String> run(String runId, MessageRequest request, RunEventBus eventBus) {
    return CompletableFuture.supplyAsync(() -> {
      var startedAt = Instant.now();
      eventBus.emit(runId, "lifecycle", Map.of("phase", "start", "startedAt", startedAt), request.sessionKey());

      eventBus.emit(runId, "tool", Map.of("phase", "start", "name", "search"), request.sessionKey());
      eventBus.emit(runId, "tool", Map.of("phase", "end", "name", "search", "result", "ok"), request.sessionKey());

      var reply = "[java-gateway] Echo: " + request.body();
      eventBus.emit(runId, "assistant", Map.of("text", reply), request.sessionKey());

      eventBus.emit(runId, "lifecycle", Map.of("phase", "end", "startedAt", startedAt, "endedAt", Instant.now()), request.sessionKey());
      return reply;
    });
  }
}
