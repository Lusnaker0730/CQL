package com.cqlplatform.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ===== ResourceNotFoundException → 404 =====

    @Test
    void handleResourceNotFoundException_shouldReturn404WithMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Measure", 42L);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleResourceNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).contains("42");
        assertThat(response.getBody().getError()).isEqualTo("Not Found");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    // ===== DuplicateResourceException → 409 =====

    @Test
    void handleDuplicateResourceException_shouldReturn409WithFieldAndValue() {
        DuplicateResourceException ex = new DuplicateResourceException("User", "username", "admin");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleDuplicateResourceException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getMessage()).contains("username");
        assertThat(response.getBody().getMessage()).contains("admin");
    }

    // ===== ValidationException → 400 with details =====

    @Test
    void handleValidationException_shouldReturn400WithDetails() {
        List<String> details = List.of("Field 'name' is required", "Field 'version' must be positive");
        ValidationException ex = new ValidationException("Validation failed", details);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getDetails()).hasSize(2);
        assertThat(response.getBody().getDetails()).containsExactlyElementsOf(details);
    }

    // ===== DataIntegrityViolationException → 409 =====

    @Test
    void handleDataIntegrityViolationException_shouldReturn409() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Unique constraint violated");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleDataIntegrityViolationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getMessage()).isEqualTo("A database constraint was violated");
    }

    // ===== CqlTranslationException → 400 with error details =====

    @Test
    void handleCqlTranslationException_shouldReturn400WithErrorMessages() {
        var error1 = com.cqlplatform.model.CqlTranslationResponse.CqlError.builder()
                .message("Could not resolve type").severity("Error").build();
        var error2 = com.cqlplatform.model.CqlTranslationResponse.CqlError.builder()
                .message("Unknown identifier").severity("Error").build();

        CqlTranslationException ex = new CqlTranslationException("CQL translation failed", List.of(error1, error2));

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleCqlTranslationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("CQL translation failed");
        assertThat(response.getBody().getDetails()).hasSize(2);
        assertThat(response.getBody().getDetails()).contains("Could not resolve type", "Unknown identifier");
    }

    // ===== CqlExecutionException → 500 (generic) or 504 (timeout) =====

    @Test
    void handleCqlExecutionException_shouldReturn500() {
        CqlExecutionException ex = new CqlExecutionException("Null pointer during evaluation");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleCqlExecutionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo("Null pointer during evaluation");
    }

    @Test
    void handleCqlExecutionException_timedOut_shouldReturn504() {
        CqlExecutionException ex = new CqlExecutionException("Execution timed out");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleCqlExecutionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(504);
    }

    // ===== FhirServerUnavailableException → 503 with structured envelope (PAT-110) =====

    @Test
    void handleFhirServerUnavailableException_shouldReturn503() {
        FhirServerUnavailableException ex = new FhirServerUnavailableException("FHIR server is down");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleFhirServerUnavailableException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).isEqualTo("FHIR server is down");
    }

    @Test
    void handleFhirServerUnavailableException_shouldCarryStructuredEnvelope() {
        // PAT-110: the FE discriminates yellow vs red banners by errorType.
        // Populate FhirRequestContext so the exception captures connection identity.
        com.cqlplatform.fhir.FhirRequestContext.set(42L, "台大 HIS");
        try {
            FhirServerUnavailableException ex = new FhirServerUnavailableException(
                    "FHIR search failed",
                    FhirServerUnavailableException.Reason.TIMEOUT);

            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleFhirServerUnavailableException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getHeaders().getFirst("Retry-After"))
                    .as("Retry-After header drives HTTP-aware client backoff")
                    .isEqualTo("30");
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getErrorType())
                    .as("machine-readable category the FE interceptor pattern-matches on")
                    .isEqualTo("FHIR_UPSTREAM_UNAVAILABLE");
            assertThat(response.getBody().getUpstream()).isNotNull();
            assertThat(response.getBody().getUpstream().getConnectionId()).isEqualTo(42L);
            assertThat(response.getBody().getUpstream().getConnectionName()).isEqualTo("台大 HIS");
            assertThat(response.getBody().getUpstream().getReason()).isEqualTo("TIMEOUT");
            assertThat(response.getBody().getUpstream().getRetryAfterSeconds()).isEqualTo(30);
        } finally {
            com.cqlplatform.fhir.FhirRequestContext.clear();
        }
    }

    // ===== CallNotPermittedException → 503 =====

    @Test
    void handleCircuitBreakerOpenException_shouldReturn503() {
        CallNotPermittedException ex = mock(CallNotPermittedException.class);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleCircuitBreakerOpenException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).contains("temporarily unavailable");
    }

    @Test
    void handleCircuitBreakerOpenException_shouldSurfaceCircuitBreakerOpenEnvelope() {
        // PAT-110: breaker-open gets its own reason value so the FE can say
        // "已自動重試 N 次、暫停中" instead of the generic TIMEOUT message.
        CallNotPermittedException ex = mock(CallNotPermittedException.class);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleCircuitBreakerOpenException(ex);

        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorType()).isEqualTo("FHIR_UPSTREAM_UNAVAILABLE");
        assertThat(response.getBody().getUpstream()).isNotNull();
        assertThat(response.getBody().getUpstream().getReason()).isEqualTo("CIRCUIT_BREAKER_OPEN");
        assertThat(response.getBody().getUpstream().getRetryAfterSeconds()).isEqualTo(60);
    }

    // ===== HttpMessageNotReadableException → 400 (PAT-117) =====

    @Test
    void handleHttpMessageNotReadable_shouldReturn400_notGeneric500() {
        // Before PAT-117 this fell through to handleGenericException → 500
        // "An internal error occurred", which surfaced in the UI as broken buttons
        // on lock / submit-for-review / share flows when the FE sent an unknown
        // field that Jackson rejected. Must be 400 — malformed client request is
        // not a server error.
        org.springframework.http.converter.HttpMessageNotReadableException ex =
                new org.springframework.http.converter.HttpMessageNotReadableException(
                        "JSON parse error: Unrecognized field \"currentUser\"",
                        (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleHttpMessageNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage())
                .as("user-facing message must be actionable, not leak Jackson internals")
                .contains("Request body")
                .doesNotContain("Jackson")
                .doesNotContain("HttpMessageNotReadableException");
    }

    // ===== HttpRequestMethodNotSupportedException → 405 (bot scanner hygiene) =====
    // Before this handler, GET probes against POST-only endpoints fell through
    // to handleGenericException → log.error("Unhandled exception", ex) and
    // returned 500. Bot scanners drove ~130 false ERROR entries/day in
    // production, drowning real failures in monitoring.

    @Test
    void handleMethodNotSupported_shouldReturn405WithAllowHeader() {
        org.springframework.web.HttpRequestMethodNotSupportedException ex =
                new org.springframework.web.HttpRequestMethodNotSupportedException(
                        "GET",
                        java.util.List.of("POST", "PUT"));

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getFirst("Allow"))
                .as("Allow header advertises supported methods per RFC 7231 §6.5.5")
                .contains("POST").contains("PUT");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(405);
        assertThat(response.getBody().getMessage()).contains("GET");
    }

    @Test
    void handleMediaTypeNotSupported_shouldReturn415() {
        org.springframework.web.HttpMediaTypeNotSupportedException ex =
                new org.springframework.web.HttpMediaTypeNotSupportedException(
                        org.springframework.http.MediaType.TEXT_PLAIN,
                        java.util.List.of(org.springframework.http.MediaType.APPLICATION_JSON));

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleMediaTypeNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(415);
        assertThat(response.getBody().getMessage()).contains("text/plain");
    }

    // ===== AccessDeniedException → 403 =====

    @Test
    void handleAccessDeniedException_shouldReturn403WithMessage() {
        AccessDeniedException ex = new AccessDeniedException("Insufficient privileges");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleAccessDeniedException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getMessage()).isEqualTo("Insufficient privileges");
    }

    // ===== IllegalArgumentException → 400 =====

    @Test
    void handleIllegalArgumentException_shouldReturn400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid FHIR resource type");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleIllegalArgumentException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid FHIR resource type");
    }

    // ===== RejectedExecutionException → 503 with Retry-After =====
    // PAT-109: when patientImportExecutor AbortPolicy rejects a bulk-import submit,
    // the API should return a retry-able 503 rather than the generic 500 fallback.

    @Test
    void handleRejectedExecutionException_shouldReturn503WithRetryAfterHeader() {
        RejectedExecutionException ex = new RejectedExecutionException(
                "Task rejected from ThreadPoolExecutor[...]");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleRejectedExecutionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(503);
        assertThat(response.getBody().getError()).isEqualTo("Service Overloaded");
        assertThat(response.getBody().getMessage())
                .as("user-facing message must be retry-oriented, not leak internals like 'ThreadPoolExecutor'")
                .contains("retry")
                .doesNotContain("ThreadPoolExecutor");
    }

    // ===== Generic Exception → 500, no leak =====

    @Test
    void handleGenericException_shouldReturn500WithGenericMessage() {
        Exception ex = new RuntimeException("secret internal details should not leak");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).doesNotContain("secret");
        assertThat(response.getBody().getMessage()).isEqualTo("An internal error occurred. Please contact support.");
    }

    // ===== PAT-160: client disconnect → void / no body =====

    @Test
    void handleClientDisconnect_asyncRequestNotUsable_shouldReturnVoidWithoutThrowing() {
        // The handler returns void; Spring uses that as "do not write any body to the
        // already-closed stream." Just verify it doesn't throw.
        org.springframework.web.context.request.async.AsyncRequestNotUsableException ex =
                new org.springframework.web.context.request.async.AsyncRequestNotUsableException(
                        "ServletOutputStream failed to write: java.io.IOException: Broken pipe");

        org.assertj.core.api.Assertions.assertThatCode(() -> handler.handleClientDisconnect(ex))
                .doesNotThrowAnyException();
    }

    @Test
    void handleGenericException_brokenPipeIOException_shouldReturnNullNotErrorBody() {
        // Defensive path: when a generic IOException carries a "Broken pipe" message,
        // we treat it as a client disconnect and return null (Spring writes nothing).
        // Without this, attempting to write a 500 ErrorResponse body to the closed
        // stream triggered HttpMessageNotWritableException — the second-order
        // "Failure in @ExceptionHandler" log entries we saw in production.
        Exception ex = new java.io.IOException("Broken pipe");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response).isNull();
    }

    @Test
    void handleGenericException_brokenPipeCausedBy_shouldDetectViaCauseChain() {
        // The disconnect IOException is often wrapped (e.g. JacksonException →
        // IOException). Walk the cause chain so we still detect it.
        Exception cause = new java.io.IOException("Broken pipe");
        Exception ex = new RuntimeException("write failed", cause);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response).isNull();
    }

    @Test
    void handleGenericException_nonDisconnectIOException_stillReturns500() {
        // Make sure we didn't over-broaden: a plain IOException without the disconnect
        // message should still produce a 500 ErrorResponse like before.
        Exception ex = new java.io.IOException("disk full");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
