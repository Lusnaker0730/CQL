package com.cqlplatform.exception;

import com.cqlplatform.model.debug.ExecutionErrorInfo;
import com.cqlplatform.util.ExecutionErrorClassifier;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

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
        // Attach structured errorInfo so editor callers can see phase=cql_translation
        // vs phase=cql_execution without regex-matching the message string.
        return buildErrorResponseWithInfo(status, "CQL Execution Error", msg, ExecutionErrorClassifier.buildErrorInfo(ex));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Error", "Invalid request parameters", errors);
    }

    // PAT-110: structured FHIR-upstream error envelope. Frontend detects
    // errorType="FHIR_UPSTREAM_UNAVAILABLE" and renders a yellow degradation
    // banner rather than a generic red error. Retry-After header lets the
    // browser's fetch retry policy (and any proxying API client) back off.
    @ExceptionHandler(FhirServerUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleFhirServerUnavailableException(FhirServerUnavailableException ex) {
        UpstreamInfo upstream = UpstreamInfo.builder()
                .connectionId(ex.getConnectionId())
                .connectionName(ex.getConnectionName())
                .reason(ex.getReason().name())
                .retryAfterSeconds(30)
                .build();
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("FHIR Server Unavailable")
                .errorType("FHIR_UPSTREAM_UNAVAILABLE")
                .message(ex.getMessage())
                .upstream(upstream)
                .requestId(MDC.get("requestId"))
                .build();
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(body);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerOpenException(CallNotPermittedException ex) {
        UpstreamInfo upstream = UpstreamInfo.builder()
                .connectionId(com.cqlplatform.fhir.FhirRequestContext.getConnectionId())
                .connectionName(com.cqlplatform.fhir.FhirRequestContext.getConnectionName())
                .reason(FhirServerUnavailableException.Reason.CIRCUIT_BREAKER_OPEN.name())
                .retryAfterSeconds(60)
                .build();
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Service Circuit Breaker Open")
                .errorType("FHIR_UPSTREAM_UNAVAILABLE")
                .message("FHIR service is temporarily unavailable due to repeated failures. Please try again later.")
                .upstream(upstream)
                .requestId(MDC.get("requestId"))
                .build();
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "60")
                .body(body);
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

    // PAT-117: malformed request body (bad JSON, wrong shape, type mismatch) is a
    // client error, not a server error. Previously fell through to handleGenericException
    // and returned 500 "An internal error occurred" — which surfaced to users as broken
    // buttons with no clue what's wrong.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String msg = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        log.warn("Malformed request body: {}", msg);
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request",
                "Request body is not valid JSON or does not match the expected schema.");
    }

    // Thrown when an AbortPolicy-backed thread pool is saturated. Today that's
    // patientImportExecutor (bulk EHR import). 503 + Retry-After lets the client
    // back off rather than interpret a 500 as a platform bug.
    @ExceptionHandler(RejectedExecutionException.class)
    public ResponseEntity<ErrorResponse> handleRejectedExecutionException(RejectedExecutionException ex) {
        log.warn("Task rejected by executor (pool saturated): {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .error("Service Overloaded")
                        .message("The server is currently handling too many concurrent requests. Please retry in a few seconds.")
                        .requestId(MDC.get("requestId"))
                        .build());
    }

    /**
     * Client disconnected mid-response — broken pipe / aborted SSE / network hiccup. The response
     * stream is already closed by the time this bubbles up, so we MUST NOT try to write an
     * ErrorResponse body: doing so previously triggered a second-order
     * {@code HttpMessageNotWritableException} inside the handler itself, surfacing as the
     * misleading {@code Failure in @ExceptionHandler ... handleGenericException} ERROR-level
     * log entries we saw in production (PAT-160).
     *
     * <p>Returning {@code void} tells Spring "nothing more to write." We log at INFO without
     * a stacktrace because the cause is the client, not us, and the upstream stacks were
     * blowing past the useful signal in our log aggregator.
     */
    @ExceptionHandler({
            AsyncRequestNotUsableException.class,
            org.apache.catalina.connector.ClientAbortException.class
    })
    public void handleClientDisconnect(Exception ex) {
        log.info("Client disconnected mid-response ({}): {}",
                ex.getClass().getSimpleName(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        // PAT-160: defensive — if a generic IOException carries a "Broken pipe" /
        // "Connection reset" message (Tomcat surfaces some disconnects this way before
        // wrapping them as AsyncRequestNotUsableException), treat it as a client
        // disconnect rather than a server error. Writing a JSON body to the dead stream
        // would just trigger a second-order HttpMessageNotWritableException.
        if (isClientDisconnect(ex)) {
            log.info("Client disconnected mid-response ({}): {}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            return null;
        }
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An internal error occurred. Please contact support.");
    }

    private static boolean isClientDisconnect(Throwable t) {
        Throwable cur = t;
        for (int i = 0; cur != null && i < 8; i++) {  // bounded walk to avoid pathological chains
            if (cur instanceof IOException) {
                String msg = cur.getMessage();
                if (msg != null && (msg.contains("Broken pipe")
                        || msg.contains("Connection reset")
                        || msg.contains("Connection closed"))) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
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
                .requestId(MDC.get("requestId"))
                .build();
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponseWithInfo(HttpStatus status, String error, String message,
                                                                      ExecutionErrorInfo errorInfo) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .requestId(MDC.get("requestId"))
                .errorInfo(errorInfo)
                .build();
        return ResponseEntity.status(status).body(response);
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private List<String> details;
        private String requestId;
        /** PAT-110: machine-readable error category. Today we only populate
         *  {@code "FHIR_UPSTREAM_UNAVAILABLE"} for structured FHIR degradation;
         *  kept generic so future error classes can slot in without wire break. */
        private String errorType;
        /** PAT-110: populated for {@code errorType=FHIR_UPSTREAM_UNAVAILABLE}. */
        private UpstreamInfo upstream;
        /** Shared structured debug info — populated for CQL execution / measure
         *  evaluation failures so callers can branch on phase without parsing message. */
        private ExecutionErrorInfo errorInfo;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UpstreamInfo {
        /** The EHR connection that failed, or null if the failing call is not
         *  tied to a specific connection (translation-time VSAC, background jobs). */
        private Long connectionId;
        /** Human-readable connection name, null when connectionId is null. */
        private String connectionName;
        /** One of {@link FhirServerUnavailableException.Reason} as a string. */
        private String reason;
        /** Seconds the client should wait before retrying. Mirrors Retry-After header. */
        private Integer retryAfterSeconds;
    }
}
