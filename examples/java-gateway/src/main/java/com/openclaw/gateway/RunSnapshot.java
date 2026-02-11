package com.openclaw.gateway;

import java.time.Instant;

public record RunSnapshot(
    String runId,
    String status,
    Instant startedAt,
    Instant endedAt,
    String error
) {
  public WaitResponse toWaitResponse() {
    return new WaitResponse(runId, status, startedAt, endedAt, error);
  }
}
