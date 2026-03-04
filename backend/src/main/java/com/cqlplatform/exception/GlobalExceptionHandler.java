package com.cqlplatform.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResourceException(DuplicateResourceException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Error", ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Conflict", "A database constraint was violated");
    }

    @ExceptionHandler(CqlTranslationException.class)
    public ResponseEntity<ErrorResponse> handleCqlTranslationException(CqlTranslationException ex) {
        List<String> details = ex.getErrors().stream()
                .map(e -> e.getMessage())
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "CQL Translation Error", ex.getMessage(), details);
    }

    @ExceptionHandler(CqlGenerationException.class)
    public ResponseEntity<ErrorResponse> handleCqlGenerationException(CqlGenerationException ex) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "CQL Generation Error", ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(CqlExecutionException.class)
    public ResponseEntity<ErrorResponse> handleCqlExecutionException(CqlExecutionException ex) {
        String msg = ex.getMessage();
        HttpStatus status;
        if (msg != null && msg.contains("timed out")) {
            status = HttpStatus.GATEWAY_TIMEOUT;
        } else if (msg != null && msg.contains("pool exhausted")) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return buildResponse(status, "CQL Execution Error", msg);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Error", "Invalid request parameters", errors);
    }

    @ExceptionHandler(FhirServerUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleFhirServerUnavailableException(FhirServerUnavailableException ex) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "FHIR Server Unavailable", ex.getMessage());
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerOpenException(CallNotPermittedException ex) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Service Circuit Breaker Open",
                "FHIR service is temporarily unavailable due to repeated failures. Please try again later.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        String message = ex.getMessage() != null
                ? ex.getMessage()
                : "You do not have permission to perform this action.";
        return buildResponse(HttpStatus.FORBIDDEN, "Access Denied", message);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(ObjectOptimisticLockingFailureException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Conflict",
                "This record was modified by another session. Please reload and try again.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An internal error occurred. Please contact support.");
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message) {
        return buildResponse(status, error, message, null);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message,
                                                         List<String> details) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .details(details)
                .build();
        return ResponseEntity.status(status).body(response);
    }

    @Data
    @Builder
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private List<String> details;
    }
}
