package com.openclaw.gateway;

import java.io.IOException;

public final class Main {
  private Main() {
  }

  public static void main(String[] args) throws IOException {
    var scheduler = new LaneScheduler();
    scheduler.setConcurrency("main", 4);
    scheduler.setConcurrency("subagent", 8);

    var bus = new RunEventBus();
    bus.subscribeAll(evt -> System.out.printf("[%s] run=%s stream=%s seq=%d data=%s%n",
        evt.timestamp(), evt.runId(), evt.stream(), evt.seq(), evt.data()));

    var gateway = new GatewayCore(scheduler, bus, new MockAgentEngine());
    var server = new GatewayHttpServer(gateway, 18789);
    server.start();

    System.out.println("Java gateway demo listening on http://127.0.0.1:18789");
    System.out.println("POST /agent?sessionKey=main&body=hello");
    System.out.println("GET  /agent/wait?runId=<id>&timeoutMs=30000");
  }
}
