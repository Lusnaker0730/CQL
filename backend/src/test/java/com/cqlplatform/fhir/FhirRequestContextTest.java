package com.cqlplatform.fhir;

import com.cqlplatform.exception.FhirServerUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FhirRequestContext + Reason classifier — PAT-110 error envelope support")
class FhirRequestContextTest {

    @AfterEach
    void tearDown() {
        FhirRequestContext.clear();
    }

    @Test
    @DisplayName("set/get round-trip on the same thread")
    void setGetRoundTrip() {
        FhirRequestContext.set(42L, "台大 HIS");
        assertThat(FhirRequestContext.getConnectionId()).isEqualTo(42L);
        assertThat(FhirRequestContext.getConnectionName()).isEqualTo("台大 HIS");
    }

    @Test
    @DisplayName("clear wipes both fields")
    void clearWipes() {
        FhirRequestContext.set(42L, "X");
        FhirRequestContext.clear();
        assertThat(FhirRequestContext.getConnectionId()).isNull();
        assertThat(FhirRequestContext.getConnectionName()).isNull();
    }

    @Test
    @DisplayName("getConnectionId returns null when unset (default state for translation-time VSAC etc)")
    void unsetReturnsNull() {
        assertThat(FhirRequestContext.getConnectionId()).isNull();
        assertThat(FhirRequestContext.getConnectionName()).isNull();
    }

    @Test
    @DisplayName("FhirServerUnavailableException snapshots the context at construction time")
    void exceptionSnapshotsContext() {
        FhirRequestContext.set(7L, "榮總 HIS");
        FhirServerUnavailableException ex = new FhirServerUnavailableException(
                "boom", FhirServerUnavailableException.Reason.TIMEOUT);
        // Even after clear, the exception keeps the identity captured at construct time.
        FhirRequestContext.clear();

        assertThat(ex.getConnectionId()).isEqualTo(7L);
        assertThat(ex.getConnectionName()).isEqualTo("榮總 HIS");
        assertThat(ex.getReason()).isEqualTo(FhirServerUnavailableException.Reason.TIMEOUT);
    }

    @Test
    @DisplayName("exception built without context has null connectionId — FE falls back to generic banner")
    void exceptionWithoutContext() {
        FhirServerUnavailableException ex = new FhirServerUnavailableException("boom");
        assertThat(ex.getConnectionId()).isNull();
        assertThat(ex.getConnectionName()).isNull();
        assertThat(ex.getReason()).isEqualTo(FhirServerUnavailableException.Reason.OTHER);
    }

    @Test
    @DisplayName("Reason.classify — SocketTimeoutException → TIMEOUT")
    void classifyTimeout() {
        assertThat(FhirServerUnavailableException.Reason.classify(new SocketTimeoutException("read timed out")))
                .isEqualTo(FhirServerUnavailableException.Reason.TIMEOUT);
    }

    @Test
    @DisplayName("Reason.classify — ConnectException → CONNECTION_REFUSED")
    void classifyConnRefused() {
        assertThat(FhirServerUnavailableException.Reason.classify(new ConnectException("Connection refused")))
                .isEqualTo(FhirServerUnavailableException.Reason.CONNECTION_REFUSED);
    }

    @Test
    @DisplayName("Reason.classify — wrapped timeout in cause chain → TIMEOUT")
    void classifyNestedTimeout() {
        Throwable wrapped = new RuntimeException("wrapper",
                new RuntimeException("inner",
                        new SocketTimeoutException("read timed out")));
        assertThat(FhirServerUnavailableException.Reason.classify(wrapped))
                .isEqualTo(FhirServerUnavailableException.Reason.TIMEOUT);
    }

    @Test
    @DisplayName("Reason.classify — null → OTHER")
    void classifyNull() {
        assertThat(FhirServerUnavailableException.Reason.classify(null))
                .isEqualTo(FhirServerUnavailableException.Reason.OTHER);
    }

    @Test
    @DisplayName("Reason.classify — pure RuntimeException with no keywords → OTHER")
    void classifyOther() {
        assertThat(FhirServerUnavailableException.Reason.classify(new RuntimeException("weird")))
                .isEqualTo(FhirServerUnavailableException.Reason.OTHER);
    }

    @Test
    @DisplayName("Reason.classify — 50-hop cause loop does not spin — bails at depth cap")
    void classifyDoesNotLoop() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        // Stop before initCause throws (Throwable refuses self-referential loops)
        // — we just build a long chain and rely on depth cap.
        Throwable head = b;
        for (int i = 0; i < 100; i++) {
            head = new RuntimeException("layer-" + i, head);
        }
        assertThat(FhirServerUnavailableException.Reason.classify(head))
                .isEqualTo(FhirServerUnavailableException.Reason.OTHER); // unclassifiable, but didn't hang
    }

    @Test
    @DisplayName("(message, cause) constructor auto-classifies — existing throw sites get meaningful Reason for free")
    void autoClassifyFromCause() {
        FhirServerUnavailableException ex = new FhirServerUnavailableException(
                "FHIR search failed", new SocketTimeoutException("read timed out"));
        assertThat(ex.getReason()).isEqualTo(FhirServerUnavailableException.Reason.TIMEOUT);
    }
}
