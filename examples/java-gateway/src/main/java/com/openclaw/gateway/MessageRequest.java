package com.openclaw.gateway;

/**
 * Normalized inbound message payload.
 */
public record MessageRequest(
    String sessionKey,
    String body,
    String channel,
    String accountId,
    String threadId,
    String idempotencyKey,
    String lane
) {
}
