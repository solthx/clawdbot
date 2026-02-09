package com.openclaw.gateway;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two-level lane scheduler: session lane serializes one session, global lane caps total throughput.
 */
public final class LaneScheduler {
  private final Executor executor;
  private final Map<String, LaneState> lanes = new ConcurrentHashMap<>();

  public LaneScheduler() {
    this(ForkJoinPool.commonPool());
  }

  public LaneScheduler(Executor executor) {
    this.executor = executor;
  }

  public void setConcurrency(String lane, int maxConcurrent) {
    lanes.computeIfAbsent(clean(lane), ignored -> new LaneState()).maxConcurrent.set(Math.max(1, maxConcurrent));
    drain(clean(lane));
  }

  public <T> CompletableFuture<T> enqueue(String lane, Task<T> task) {
    var state = lanes.computeIfAbsent(clean(lane), ignored -> new LaneState());
    var future = new CompletableFuture<T>();
    state.queue.add(new QueueEntry(() -> task.run().whenComplete((value, err) -> {
      if (err != null) {
        future.completeExceptionally(err);
      } else {
        future.complete(value);
      }
    })));
    drain(clean(lane));
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
      // If new tasks arrived while we were draining, schedule another pass.
      if (!state.queue.isEmpty() && state.active.get() < state.maxConcurrent.get()) {
        drain(lane);
      }
    }
  }

  private String clean(String lane) {
    if (lane == null || lane.isBlank()) {
      return "main";
    }
    return lane.trim();
  }

  private static final class LaneState {
    final Queue<QueueEntry> queue = new ConcurrentLinkedQueue<>();
    final AtomicInteger active = new AtomicInteger();
    final AtomicInteger maxConcurrent = new AtomicInteger(1);
    final java.util.concurrent.atomic.AtomicBoolean draining = new java.util.concurrent.atomic.AtomicBoolean(false);
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
