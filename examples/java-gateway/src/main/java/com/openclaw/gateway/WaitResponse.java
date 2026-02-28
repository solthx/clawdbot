package com.openclaw.gateway;

import java.time.Instant;

public record WaitResponse(
    String runId,
    String status,
    Instant startedAt,
    Instant endedAt,
    String error
) {
}
