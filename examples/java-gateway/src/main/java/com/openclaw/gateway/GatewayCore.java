package com.openclaw.gateway;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Core gateway flow:
 * 1) accept inbound message and return runId immediately
 * 2) execute in session lane + global lane
 * 3) expose wait/run events APIs.
 */
public final class GatewayCore {
  private final LaneScheduler scheduler;
  private final RunEventBus eventBus;
  private final AgentEngine agentEngine;
  private final Map<String, String> runByIdempotency = new ConcurrentHashMap<>();

  public GatewayCore(LaneScheduler scheduler, RunEventBus eventBus, AgentEngine agentEngine) {
    this.scheduler = scheduler;
    this.eventBus = eventBus;
    this.agentEngine = agentEngine;
  }

  public AcceptedRun accept(MessageRequest request, String requestedLane) {
    var idempotency = request.idempotencyKey();
    if (idempotency != null && !idempotency.isBlank()) {
      var existing = runByIdempotency.get(idempotency);
      if (existing != null) {
        return new AcceptedRun(existing, true);
      }
    }

    var runId = UUID.randomUUID().toString();
    if (idempotency != null && !idempotency.isBlank()) {
      runByIdempotency.putIfAbsent(idempotency, runId);
    }

    var sessionLane = "session:" + request.sessionKey().trim();
    var globalLane = requestedLane == null || requestedLane.isBlank() ? "main" : requestedLane.trim();

    scheduler.enqueue(sessionLane, () ->
        scheduler.enqueue(globalLane, () -> agentEngine.run(runId, request, eventBus))
    );

    return new AcceptedRun(runId, false);
  }

  public CompletableFuture<RunSnapshot> waitForRun(String runId, Duration timeout) {
    return eventBus.waitFor(runId)
        .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .exceptionally(err -> new RunSnapshot(runId, "timeout", null, null, err.getMessage()));
  }

  public RunEventBus events() {
    return eventBus;
  }

  public record AcceptedRun(String runId, boolean cached) {
    public Map<String, Object> asMap() {
      return Map.of(
          "runId", runId,
          "status", "accepted",
          "cached", cached
      );
    }
  }
}
