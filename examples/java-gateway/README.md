# Java Gateway (JDK 17)

This is a learning-oriented Java implementation of OpenClaw gateway core ideas:

- **Accepted + async run** (`/agent` returns `runId` immediately)
- **agent.wait semantics** (`/agent/wait` blocks on lifecycle end/error)
- **Event streams** (`lifecycle`, `tool`, `assistant`)
- **Lane scheduler** (session lane + global lane)

It is intentionally minimal so you can evolve it into a distributed design.

## Run

```bash
cd examples/java-gateway
mvn -q compile
mvn -q exec:java -Dexec.mainClass=com.openclaw.gateway.Main
```

(If `exec-maven-plugin` is unavailable in your environment, run with `java -cp target/classes ...`.)

## Try it

```bash
# 1) submit
curl -X POST 'http://127.0.0.1:18789/agent?sessionKey=main&body=hello&idempotencyKey=abc'

# 2) wait
curl 'http://127.0.0.1:18789/agent/wait?runId=<RUN_ID>&timeoutMs=30000'
```

## Source map

- `GatewayCore`:
  - accept request, dedupe by idempotency key
  - enqueue into `session:<key>` then global lane
  - expose wait API via `RunEventBus`
- `LaneScheduler`:
  - in-memory lane queue with configurable concurrency
- `RunEventBus`:
  - pub/sub events and run completion snapshots
- `MockAgentEngine`:
  - emits lifecycle/tool/assistant events to simulate a real agent loop
- `GatewayHttpServer`:
  - tiny HTTP shell (`/agent`, `/agent/wait`)

## How to evolve to distributed mode

Recommended refactor path:

1. Replace in-memory `LaneScheduler` with a durable queue backend.
2. Replace `RunEventBus` with pub/sub (Kafka, NATS, Redis Streams).
3. Persist run metadata (`runId -> status`) in a database.
4. Keep `GatewayCore` orchestration API stable; swap implementations behind interfaces.

A practical split:

- **Gateway API service**: accept, wait, stream endpoints.
- **Run orchestrator**: lane admission control + retries.
- **Worker service**: executes `AgentEngine`.
- **Event service**: fan-out lifecycle/tool/assistant streams.

This mirrors the same control-plane/data-plane separation used by OpenClaw's gateway design.
