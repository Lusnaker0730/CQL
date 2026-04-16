package com.cqlplatform.exception;

/**
 * Thrown when a test case's FHIR Bundle JSON cannot be parsed.
 * Tagged so TestCaseService can attribute failure to the BUNDLE_PARSE phase
 * instead of CQL_EXECUTION.
 */
public class BundleParseException extends RuntimeException {
    public BundleParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
