package com.eventzone.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionTest {

    @Test
    void getters_returnConfiguredValues() {
        ApiException ex = new ApiException(HttpStatus.BAD_REQUEST, "TEST_CODE", "Test message") {};

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo("TEST_CODE");
        assertThat(ex.getMessage()).isEqualTo("Test message");
    }
}
