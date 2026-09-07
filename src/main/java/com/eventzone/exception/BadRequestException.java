package com.eventzone.exception;

import org.springframework.http.HttpStatus;

/**
 * Used for business-rule validation failures (e.g. insufficient seats,
 * invalid quantity, booking an inactive event, cancelling an already
 * cancelled booking). Mapped to the same VALIDATION_ERROR code as bean
 * validation failures.
 */
public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
