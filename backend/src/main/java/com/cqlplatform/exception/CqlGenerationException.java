package com.cqlplatform.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class CqlGenerationException extends RuntimeException {
    private final List<String> details;

    public CqlGenerationException(String message, List<String> details) {
        super(message);
        this.details = details;
    }

    public CqlGenerationException(String message) {
        super(message);
        this.details = List.of();
    }

    public CqlGenerationException(String message, Throwable cause) {
        super(message, cause);
        this.details = List.of();
    }
}
