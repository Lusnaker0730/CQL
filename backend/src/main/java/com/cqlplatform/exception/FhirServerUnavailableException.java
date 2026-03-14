package com.cqlplatform.exception;

public class FhirServerUnavailableException extends RuntimeException {

    public FhirServerUnavailableException(String message) {
        super(message);
    }

    public FhirServerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
