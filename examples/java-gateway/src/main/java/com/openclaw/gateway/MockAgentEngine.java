package com.openclaw.gateway;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Demo runtime used to illustrate lifecycle/tool/assistant streams.
 */
@Component
public final class MockAgentEngine implements AgentEngine {
  @Override
  public Mono<String> run(String runId, MessageRequest request, RunEventBus eventBus) {
    return Mono.defer(() -> {
      var startedAt = Instant.now();
      eventBus.emit(
          runId,
          "lifecycle",
          Map.of("phase", "start", "startedAt", startedAt),
          request.sessionKey());

      eventBus.emit(runId, "tool", Map.of("phase", "start", "name", "search"), request.sessionKey());
      eventBus.emit(
          runId,
          "tool",
          Map.of("phase", "end", "name", "search", "result", "ok"),
          request.sessionKey());

      var text = "[spring-gateway] Echo: " + request.body();
      eventBus.emit(runId, "assistant", Map.of("text", text), request.sessionKey());

      eventBus.emit(
          runId,
          "lifecycle",
          Map.of("phase", "end", "startedAt", startedAt, "endedAt", Instant.now()),
          request.sessionKey());

      return Mono.just(text);
    });
  }
}
