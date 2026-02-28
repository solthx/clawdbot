package com.openclaw.gateway;

public record AcceptedRunResponse(String runId, String status, long acceptedAt, boolean cached) {
}
