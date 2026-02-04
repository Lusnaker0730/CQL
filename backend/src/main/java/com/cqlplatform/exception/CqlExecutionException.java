package com.cqlplatform.exception;

public class CqlExecutionException extends RuntimeException {
    public CqlExecutionException(String message) {
        super(message);
    }

    public CqlExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
