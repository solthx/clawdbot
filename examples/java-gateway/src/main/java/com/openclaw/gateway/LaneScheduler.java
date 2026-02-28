package com.openclaw.gateway;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lane-aware in-memory scheduler.
 *
 * <p>It mirrors OpenClaw's shape: session lane serialization + global lane concurrency cap.
 */
public final class LaneScheduler {
  private final Executor executor;
  private final Map<String, LaneState> lanes = new ConcurrentHashMap<>();

  public LaneScheduler() {
    this(Executors.newCachedThreadPool());
  }

  public LaneScheduler(Executor executor) {
    this.executor = executor;
  }

  public void setConcurrency(String lane, int maxConcurrent) {
    var key = normalizeLane(lane);
    lanes.computeIfAbsent(key, ignored -> new LaneState())
        .maxConcurrent
        .set(Math.max(1, maxConcurrent));
    drain(key);
  }

  public <T> CompletableFuture<T> enqueue(String lane, Task<T> task) {
    var key = normalizeLane(lane);
    var state = lanes.computeIfAbsent(key, ignored -> new LaneState());
    var future = new CompletableFuture<T>();

    state.queue.add(new QueueEntry(() -> {
      try {
        task.run().whenComplete((value, err) -> {
          if (err != null) {
            future.completeExceptionally(err);
          } else {
            future.complete(value);
          }
        });
      } catch (Throwable err) {
        future.completeExceptionally(err);
      }
    }));

    drain(key);
    return future;
  }

  private void drain(String lane) {
    var state = lanes.computeIfAbsent(lane, ignored -> new LaneState());
    if (!state.draining.compareAndSet(false, true)) {
      return;
    }

    try {
      while (state.active.get() < state.maxConcurrent.get()) {
        var entry = state.queue.poll();
        if (entry == null) {
          break;
        }

        state.active.incrementAndGet();
        executor.execute(() -> {
          try {
            entry.run();
          } finally {
            state.active.decrementAndGet();
            drain(lane);
          }
        });
      }
    } finally {
      state.draining.set(false);
      if (!state.queue.isEmpty() && state.active.get() < state.maxConcurrent.get()) {
        drain(lane);
      }
    }
  }

  private String normalizeLane(String lane) {
    if (lane == null || lane.isBlank()) {
      return "main";
    }
    return lane.trim();
  }

  private static final class LaneState {
    private final Queue<QueueEntry> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger maxConcurrent = new AtomicInteger(1);
    private final AtomicBoolean draining = new AtomicBoolean(false);
  }

  private record QueueEntry(Runnable runnable) {
    void run() {
      runnable.run();
    }
  }

  @FunctionalInterface
  public interface Task<T> {
    CompletableFuture<T> run();
  }
}
