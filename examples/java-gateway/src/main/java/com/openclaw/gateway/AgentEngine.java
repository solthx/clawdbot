package com.openclaw.gateway;

import java.util.concurrent.CompletableFuture;

/**
 * Agent runtime abstraction. Replace this with real LLM/tool implementation.
 */
public interface AgentEngine {
  CompletableFuture<String> run(String runId, MessageRequest request, RunEventBus eventBus);
}
