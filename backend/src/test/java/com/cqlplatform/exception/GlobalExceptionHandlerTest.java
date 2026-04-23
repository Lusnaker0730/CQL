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

    // ===== FhirServerUnavailableException → 503 =====

    @Test
    void handleFhirServerUnavailableException_shouldReturn503() {
        FhirServerUnavailableException ex = new FhirServerUnavailableException("FHIR server is down");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleFhirServerUnavailableException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).isEqualTo("FHIR server is down");
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
}
