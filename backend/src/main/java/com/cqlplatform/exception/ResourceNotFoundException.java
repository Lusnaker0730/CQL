package com.cqlplatform.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceType, Object id) {
        super(resourceType + " not found: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
