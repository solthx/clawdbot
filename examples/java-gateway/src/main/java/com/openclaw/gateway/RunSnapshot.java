package com.openclaw.gateway;

import java.time.Instant;

/**
 * Final status queried by agent.wait equivalent.
 */
public record RunSnapshot(
    String runId,
    String status,
    Instant startedAt,
    Instant endedAt,
    String error
) {
}
