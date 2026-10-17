package com.turismo.common;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

public record ErrorResponse(int status, String error, String mensaje, LocalDateTime timestamp) {
    public static ErrorResponse of(HttpStatus status, String mensaje) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), mensaje, LocalDateTime.now());
    }
}