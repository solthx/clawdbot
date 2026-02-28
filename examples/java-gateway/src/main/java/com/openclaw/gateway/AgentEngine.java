package com.openclaw.gateway;

import reactor.core.publisher.Mono;

/**
 * Agent runtime abstraction.
 */
public interface AgentEngine {
  Mono<String> run(String runId, MessageRequest request, RunEventBus eventBus);
}
