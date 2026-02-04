package com.cqlplatform.exception;

import com.cqlplatform.model.CqlTranslationResponse.CqlError;
import lombok.Getter;

import java.util.List;

@Getter
public class CqlTranslationException extends RuntimeException {
    private final List<CqlError> errors;

    public CqlTranslationException(String message, List<CqlError> errors) {
        super(message);
        this.errors = errors;
    }

    public CqlTranslationException(String message) {
        super(message);
        this.errors = List.of();
    }
}
