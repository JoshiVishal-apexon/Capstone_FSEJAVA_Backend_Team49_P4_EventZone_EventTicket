package com.eventzone.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringControllerTest {

    @Test
    void getStatus_returnsSummary() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MonitoringController controller = new MonitoringController(registry);

        var response = controller.getStatus();

        assertThat(response.status()).isNotNull();
        assertThat(response.uptimeSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(response.totalRequests()).isEqualTo(0);
    }

    @Test
    void getStatus_includesRecordedHttpMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer.builder("http.server.requests")
                .tag("method", "GET")
                .tag("uri", "/api/events")
                .tag("status", "200")
                .tag("outcome", "SUCCESS")
                .register(registry)
                .record(245, TimeUnit.MILLISECONDS);

        MonitoringController controller = new MonitoringController(registry);
        var response = controller.getStatus();

        assertThat(response.status()).isEqualTo("Healthy");
        assertThat(response.totalRequests()).isEqualTo(1);
        assertThat(response.endpoints()).hasSize(1);
        assertThat(response.endpoints().get(0).path()).isEqualTo("/api/events");
        assertThat(response.endpoints().get(0).method()).isEqualTo("GET");
        assertThat(response.endpoints().get(0).avgResponseTimeMs()).isGreaterThan(0.0);
    }

    @Test
    void getStatus_handlesMissingTags() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer.builder("http.server.requests")
                .tag("uri", "/api/auth/login")
                .register(registry)
                .record(120, TimeUnit.MILLISECONDS);

        MonitoringController controller = new MonitoringController(registry);
        var response = controller.getStatus();

        assertThat(response.totalRequests()).isEqualTo(1);
        assertThat(response.endpoints()).hasSize(1);
        assertThat(response.endpoints().get(0).method()).isEqualTo("UNKNOWN");
        assertThat(response.endpoints().get(0).status()).isEqualTo("UNKNOWN");
        assertThat(response.endpoints().get(0).outcome()).isEqualTo("UNKNOWN");
    }
}
