# Java Gateway (Spring Boot + Reactor, JDK 17)

This example implements a learning-oriented gateway architecture aligned with OpenClaw core ideas:

- **Accepted + async run** (`POST /agent` returns `runId` immediately)
- **`agent.wait` semantics** (`GET /agent/wait` blocks until lifecycle end/error or timeout)
- **Event streams** (`lifecycle`, `tool`, `assistant`) via SSE
- **Lane scheduler** (`session:<key>` serialization + global lane concurrency)

It is intentionally in-memory and interface-first so you can evolve it into a distributed version.

## Endpoints

- `POST /agent`
- `GET /agent/wait?runId=<id>&timeoutMs=30000`
- `GET /agent/events/{runId}` (SSE)

## Run

```bash
cd examples/java-gateway
mvn spring-boot:run
```

Server runs on `http://127.0.0.1:18789`.

## Quick try

```bash
# 1) submit (accepted immediately)
curl -s -X POST 'http://127.0.0.1:18789/agent' \
  -H 'content-type: application/json' \
  -d '{
    "sessionKey": "main",
    "body": "hello gateway",
    "channel": "internal",
    "idempotencyKey": "demo-1"
  }'

# 2) wait for lifecycle terminal
curl -s 'http://127.0.0.1:18789/agent/wait?runId=<RUN_ID>&timeoutMs=30000'

# 3) stream events
curl -N 'http://127.0.0.1:18789/agent/events/<RUN_ID>'
```

## Source map

- `GatewayCore`
  - request validation + idempotency cache
  - lane admission (`session:<key>` then global lane)
  - wait API + event stream API
- `LaneScheduler`
  - in-memory lane queue with per-lane concurrency cap
- `RunEventBus`
  - publishes events and completes run waiters on lifecycle `end/error`
- `GatewayHttpServer`
  - WebFlux controller
- `AgentEngine` + `MockAgentEngine`
  - pluggable runtime interface + demo implementation

## Distributed evolution path

A clean progression from this sample:

1. Replace `LaneScheduler` with durable queue-based admission (e.g., Redis Streams / Kafka / SQS).
2. Replace `RunEventBus` with pub/sub fanout and durable run-state storage.
3. Keep `GatewayCore` orchestration contract stable; swap implementation behind interfaces.
4. Split services:
   - API gateway service (`/agent`, `/agent/wait`, `/agent/events`)
   - orchestrator service (lane admission + retries)
   - worker service (`AgentEngine` execution)
   - event service (stream fanout + replay)

This keeps control-plane and data-plane concerns separable from day one.
