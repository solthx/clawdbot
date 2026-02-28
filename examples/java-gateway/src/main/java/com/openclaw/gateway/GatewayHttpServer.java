package com.openclaw.gateway;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * WebFlux controller for learning endpoints.
 */
@RestController
public final class GatewayHttpServer {
  private final GatewayCore gateway;

  public GatewayHttpServer(GatewayCore gateway) {
    this.gateway = gateway;
  }

  @PostMapping(path = "/agent", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<AcceptedRunResponse> agent(@RequestBody MessageRequest request) {
    return gateway.accept(request);
  }

  @GetMapping(path = "/agent/wait", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<WaitResponse> waitFor(
      @RequestParam("runId") String runId,
      @RequestParam(name = "timeoutMs", defaultValue = "30000") long timeoutMs) {
    return gateway.waitFor(runId, Duration.ofMillis(Math.max(1L, timeoutMs)));
  }

  @GetMapping(path = "/agent/events/{runId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<GatewayEvent>> events(@PathVariable("runId") String runId) {
    return gateway.events(runId)
        .map(event -> ServerSentEvent.<GatewayEvent>builder().event(event.stream()).id(String.valueOf(event.seq())).data(event).build());
  }
}
