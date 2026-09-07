package com.eventzone.dto.monitoring;

import java.util.List;

public record MonitoringSummaryResponse(
        String status,
        long uptimeSeconds,
        long totalRequests,
        List<EndpointMetricResponse> endpoints
) {
}
