package com.eventzone.dto.common;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public record ErrorResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant timestamp,
        String path,
        String error,
        String message
) {
    public static ErrorResponse of(String path, String error, String message) {
        Instant now = Instant.now().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toInstant();
        return new ErrorResponse(now, path, error, message);
    }
}
