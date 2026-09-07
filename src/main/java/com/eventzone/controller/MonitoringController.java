package com.eventzone.controller;

import com.eventzone.dto.common.ErrorResponse;
import com.eventzone.dto.monitoring.EndpointMetricResponse;
import com.eventzone.dto.monitoring.MonitoringSummaryResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful response"),
        @ApiResponse(responseCode = "400", description = "Validation or bad request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class MonitoringController {

    private final MeterRegistry meterRegistry;

    public MonitoringController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/monitoring/status")
    public MonitoringSummaryResponse getStatus() {
        List<EndpointMetricResponse> endpoints = meterRegistry.find("http.server.requests")
                .timers()
                .stream()
                .map(this::toEndpointMetric)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(EndpointMetricResponse::requests).reversed())
                .toList();

        long totalRequests = endpoints.stream().mapToLong(EndpointMetricResponse::requests).sum();

        return new MonitoringSummaryResponse(
                totalRequests > 0 ? "Healthy" : "No traffic yet",
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                totalRequests,
                endpoints
        );
    }

    private EndpointMetricResponse toEndpointMetric(Timer timer) {
        String method = getTagValue(timer, "method");
        String path = getTagValue(timer, "uri");
        String status = getTagValue(timer, "status");
        String outcome = getTagValue(timer, "outcome");

        if (path == null || path.isBlank()) {
            return null;
        }

        double avgMs = timer.mean(TimeUnit.MILLISECONDS);
        double maxMs = timer.max(TimeUnit.MILLISECONDS);
        double totalMs = timer.totalTime(TimeUnit.MILLISECONDS);

        return new EndpointMetricResponse(
                method == null ? "UNKNOWN" : method,
                path,
                status == null ? "UNKNOWN" : status,
                outcome == null ? "UNKNOWN" : outcome,
                timer.count(),
                round(avgMs),
                round(maxMs),
                round(totalMs)
        );
    }

    private String getTagValue(Timer timer, String tagName) {
        return timer.getId().getTag(tagName);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
