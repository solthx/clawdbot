package com.openclaw.gateway;

import java.time.Instant;
import java.util.Map;

/**
 * Stream event emitted by one run.
 */
public record GatewayEvent(
    String runId,
    long seq,
    String stream,
    Instant timestamp,
    Map<String, Object> data,
    String sessionKey
) {
}
