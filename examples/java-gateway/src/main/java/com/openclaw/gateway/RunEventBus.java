package com.openclaw.gateway;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * In-memory event bus for per-run event streams and run completion snapshots.
 */
public final class RunEventBus {
  private final Map<String, AtomicLong> seqByRun = new ConcurrentHashMap<>();
  private final Map<String, Sinks.Many<GatewayEvent>> streams = new ConcurrentHashMap<>();
  private final Map<String, Sinks.One<RunSnapshot>> waiters = new ConcurrentHashMap<>();
  private final Map<String, RunSnapshot> snapshots = new ConcurrentHashMap<>();

  public void emit(String runId, String stream, Map<String, Object> data, String sessionKey) {
    var seq = seqByRun.computeIfAbsent(runId, ignored -> new AtomicLong()).incrementAndGet();
    var evt = new GatewayEvent(runId, seq, stream, Instant.now(), data, sessionKey);
    streamSink(runId).tryEmitNext(evt);

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
        (String) data.get("error"));

    snapshots.put(runId, snapshot);
    waiterSink(runId).tryEmitValue(snapshot);
    streamSink(runId).tryEmitComplete();
  }

  public Flux<GatewayEvent> events(String runId) {
    return streamSink(runId).asFlux();
  }

  public Mono<RunSnapshot> waitFor(String runId) {
    var cached = snapshots.get(runId);
    if (cached != null) {
      return Mono.just(cached);
    }
    return waiterSink(runId).asMono();
  }

  private Sinks.Many<GatewayEvent> streamSink(String runId) {
    return streams.computeIfAbsent(runId, ignored -> Sinks.many().multicast().onBackpressureBuffer());
  }

  private Sinks.One<RunSnapshot> waiterSink(String runId) {
    return waiters.computeIfAbsent(runId, ignored -> Sinks.one());
  }
}
