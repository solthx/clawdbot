package com.openclaw.gateway;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

/**
 * Gateway orchestration core aligned with OpenClaw patterns:
 * accepted response -> session lane -> global lane -> lifecycle completion.
 */
@Service
public final class GatewayCore {
  private final LaneScheduler scheduler = new LaneScheduler();
  private final RunEventBus eventBus = new RunEventBus();
  private final AgentEngine agentEngine;
  private final Map<String, String> runByIdempotency = new ConcurrentHashMap<>();

  public GatewayCore(AgentEngine agentEngine) {
    this.agentEngine = agentEngine;
    scheduler.setConcurrency("main", 4);
    scheduler.setConcurrency("subagent", 8);
  }

  public Mono<AcceptedRunResponse> accept(MessageRequest request) {
    if (request.sessionKey() == null || request.sessionKey().isBlank()) {
      return Mono.error(new IllegalArgumentException("sessionKey is required"));
    }
    if (request.body() == null || request.body().isBlank()) {
      return Mono.error(new IllegalArgumentException("body is required"));
    }

    var idempotency = blankToNull(request.idempotencyKey());
    if (idempotency != null) {
      var existingRunId = runByIdempotency.get(idempotency);
      if (existingRunId != null) {
        return Mono.just(new AcceptedRunResponse(existingRunId, "accepted", System.currentTimeMillis(), true));
      }
    }

    var runId = UUID.randomUUID().toString();
    if (idempotency != null) {
      runByIdempotency.putIfAbsent(idempotency, runId);
    }

    var sessionLane = "session:" + request.sessionKey().trim();
    var globalLane = blankToNull(request.lane()) == null ? "main" : request.lane().trim();

    scheduler.enqueue(
        sessionLane,
        () -> scheduler.enqueue(globalLane, () -> agentEngine.run(runId, request, eventBus).toFuture()));

    return Mono.just(new AcceptedRunResponse(runId, "accepted", System.currentTimeMillis(), false));
  }

  public Mono<WaitResponse> waitFor(String runId, Duration timeout) {
    return eventBus.waitFor(runId)
        .timeout(timeout)
        .map(RunSnapshot::toWaitResponse)
        .onErrorResume(err -> Mono.just(new WaitResponse(runId, "timeout", null, null, err.getMessage())));
  }

  public reactor.core.publisher.Flux<GatewayEvent> events(String runId) {
    return eventBus.events(runId);
  }

  public RunEventBus eventBus() {
    return eventBus;
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }
}
