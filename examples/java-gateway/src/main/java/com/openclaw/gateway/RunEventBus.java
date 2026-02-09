package com.openclaw.gateway;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * In-memory event bus + run completion cache.
 */
public final class RunEventBus {
  private final Map<String, AtomicLong> seqByRun = new ConcurrentHashMap<>();
  private final Map<String, List<Consumer<GatewayEvent>>> listenersByRun = new ConcurrentHashMap<>();
  private final Set<Consumer<GatewayEvent>> globalListeners = ConcurrentHashMap.newKeySet();
  private final Map<String, CompletableFuture<RunSnapshot>> waiters = new ConcurrentHashMap<>();
  private final Map<String, RunSnapshot> snapshots = new ConcurrentHashMap<>();

  public void emit(String runId, String stream, Map<String, Object> data, String sessionKey) {
    var seq = seqByRun.computeIfAbsent(runId, ignored -> new AtomicLong()).incrementAndGet();
    var event = new GatewayEvent(runId, seq, stream, Instant.now(), data, sessionKey);

    globalListeners.forEach(listener -> listener.accept(event));
    listenersByRun.getOrDefault(runId, List.of()).forEach(listener -> listener.accept(event));

    if (!"lifecycle".equals(stream)) {
      return;
    }

    var phase = String.valueOf(data.getOrDefault("phase", ""));
    if (!"end".equals(phase) && !"error".equals(phase)) {
      return;
    }

    var snapshot = new RunSnapshot(
        runId,
        "error".equals(phase) ? "error" : "ok",
        (Instant) data.getOrDefault("startedAt", Instant.now()),
        (Instant) data.getOrDefault("endedAt", Instant.now()),
        (String) data.get("error")
    );
    snapshots.put(runId, snapshot);
    waiters.computeIfAbsent(runId, ignored -> new CompletableFuture<>()).complete(snapshot);
  }

  public AutoCloseable subscribeRun(String runId, Consumer<GatewayEvent> listener) {
    listenersByRun.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(listener);
    return () -> listenersByRun.getOrDefault(runId, List.of()).remove(listener);
  }

  public AutoCloseable subscribeAll(Consumer<GatewayEvent> listener) {
    globalListeners.add(listener);
    return () -> globalListeners.remove(listener);
  }

  public CompletableFuture<RunSnapshot> waitFor(String runId) {
    var cached = snapshots.get(runId);
    if (cached != null) {
      return CompletableFuture.completedFuture(cached);
    }
    return waiters.computeIfAbsent(runId, ignored -> new CompletableFuture<>());
  }
}
