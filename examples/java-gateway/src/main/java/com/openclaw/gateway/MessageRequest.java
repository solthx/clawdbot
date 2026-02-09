package com.openclaw.gateway;

/**
 * Normalized inbound message passed to the gateway core.
 */
public record MessageRequest(
    String sessionKey,
    String body,
    String channel,
    String accountId,
    String threadId,
    String idempotencyKey
) {
  public MessageRequest {
    if (sessionKey == null || sessionKey.isBlank()) {
      throw new IllegalArgumentException("sessionKey is required");
    }
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("body is required");
    }
  }
}
