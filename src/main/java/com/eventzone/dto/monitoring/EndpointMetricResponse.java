package com.eventzone.dto.monitoring;

public record EndpointMetricResponse(
        String method,
        String path,
        String status,
        String outcome,
        long requests,
        double avgResponseTimeMs,
        double maxResponseTimeMs,
        double totalResponseTimeMs
) {
}
