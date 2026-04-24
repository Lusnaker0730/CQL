package com.cqlplatform.fhir;

import com.cqlplatform.exception.FhirServerUnavailableException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PAT-112c: HAPI servers sometimes return HTTP 200 with a Bundle carrying an
 * OperationOutcome(severity=error|fatal) instead of a FHIR-level HTTP error.
 * Without inspection, this "success" looks indistinguishable from an empty
 * legitimate result. These tests lock the detection contract.
 */
@DisplayName("FhirErrorInspector — 200-OK-with-OperationOutcome detection (PAT-112c)")
class FhirErrorInspectorTest {

    @Test
    @DisplayName("null bundle: no-op, never throws")
    void nullBundle() {
        assertThatCode(() -> FhirErrorInspector.assertNoErrors(null, "test"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("empty bundle (no entries): no-op — legitimate true-negative")
    void emptyBundle() {
        Bundle b = new Bundle();
        b.setType(Bundle.BundleType.SEARCHSET);
        b.setTotal(0);
        assertThatCode(() -> FhirErrorInspector.assertNoErrors(b, "test"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bundle with only data resources: no-op")
    void bundleWithOnlyData() {
        Bundle b = new Bundle();
        Patient p = new Patient();
        p.setId("Patient/1");
        b.addEntry().setResource(p);
        assertThatCode(() -> FhirErrorInspector.assertNoErrors(b, "test"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bundle with informational OperationOutcome: no-op — info != error")
    void informationalOutcomeIsIgnored() {
        Bundle b = new Bundle();
        OperationOutcome oo = new OperationOutcome();
        oo.addIssue().setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                .setDiagnostics("partial result truncated");
        b.addEntry().setResource(oo);
        assertThatCode(() -> FhirErrorInspector.assertNoErrors(b, "test"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bundle with warning OperationOutcome: no-op — warnings are not outages")
    void warningOutcomeIsIgnored() {
        Bundle b = new Bundle();
        OperationOutcome oo = new OperationOutcome();
        oo.addIssue().setSeverity(OperationOutcome.IssueSeverity.WARNING)
                .setDiagnostics("deprecated parameter");
        b.addEntry().setResource(oo);
        assertThatCode(() -> FhirErrorInspector.assertNoErrors(b, "test"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bundle with ERROR-severity OperationOutcome: throws FhirServerUnavailableException")
    void errorOutcomeThrows() {
        Bundle b = new Bundle();
        OperationOutcome oo = new OperationOutcome();
        oo.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(OperationOutcome.IssueType.TRANSIENT)
                .setDiagnostics("temporary database error");
        b.addEntry().setResource(oo);

        assertThatThrownBy(() -> FhirErrorInspector.assertNoErrors(b, "bulk fetch Observation"))
                .isInstanceOf(FhirServerUnavailableException.class)
                .hasMessageContaining("bulk fetch Observation")
                .hasMessageContaining("error/transient")
                .hasMessageContaining("temporary database error");
    }

    @Test
    @DisplayName("bundle with FATAL-severity OperationOutcome: throws")
    void fatalOutcomeThrows() {
        Bundle b = new Bundle();
        OperationOutcome oo = new OperationOutcome();
        oo.addIssue().setSeverity(OperationOutcome.IssueSeverity.FATAL)
                .setDiagnostics("unrecoverable index corruption");
        b.addEntry().setResource(oo);

        assertThatThrownBy(() -> FhirErrorInspector.assertNoErrors(b, "test"))
                .isInstanceOf(FhirServerUnavailableException.class)
                .hasMessageContaining("fatal");
    }

    @Test
    @DisplayName("transaction response: error in entry.response.outcome also detected")
    void perEntryResponseOutcome() {
        Bundle b = new Bundle();
        b.setType(Bundle.BundleType.TRANSACTIONRESPONSE);
        OperationOutcome oo = new OperationOutcome();
        oo.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setDiagnostics("conflict during conditional update");
        b.addEntry().getResponse().setStatus("409").setOutcome(oo);

        assertThatThrownBy(() -> FhirErrorInspector.assertNoErrors(b, "Patient batch"))
                .isInstanceOf(FhirServerUnavailableException.class)
                .hasMessageContaining("conflict");
    }

    @Test
    @DisplayName("multiple errors: all aggregated into the message")
    void multipleErrorsAggregated() {
        Bundle b = new Bundle();
        OperationOutcome oo = new OperationOutcome();
        oo.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setDiagnostics("first thing broke");
        oo.addIssue().setSeverity(OperationOutcome.IssueSeverity.FATAL)
                .setDiagnostics("then the second");
        b.addEntry().setResource(oo);

        assertThatThrownBy(() -> FhirErrorInspector.assertNoErrors(b, "test"))
                .isInstanceOf(FhirServerUnavailableException.class)
                .hasMessageContaining("first thing broke")
                .hasMessageContaining("then the second");
    }

    @Test
    @DisplayName("mixed: data + info OO + error OO — throws (even with valid data present)")
    void mixedDataAndError() {
        Bundle b = new Bundle();
        Patient p = new Patient();
        p.setId("Patient/1");
        b.addEntry().setResource(p);

        OperationOutcome info = new OperationOutcome();
        info.addIssue().setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                .setDiagnostics("ok");
        b.addEntry().setResource(info);

        OperationOutcome err = new OperationOutcome();
        err.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setDiagnostics("partial failure");
        b.addEntry().setResource(err);

        // Partial data + error must NOT be accepted as success — exactly the
        // patient-safety concern this inspector exists for.
        assertThatThrownBy(() -> FhirErrorInspector.assertNoErrors(b, "test"))
                .isInstanceOf(FhirServerUnavailableException.class);
    }

    @Test
    @DisplayName("FhirServerUnavailableException carries Reason.OTHER (no specific cause)")
    void exceptionReasonDefaultsToOther() {
        Bundle b = new Bundle();
        OperationOutcome oo = new OperationOutcome();
        oo.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics("x");
        b.addEntry().setResource(oo);

        try {
            FhirErrorInspector.assertNoErrors(b, "test");
        } catch (FhirServerUnavailableException e) {
            assertThat(e.getReason()).isEqualTo(FhirServerUnavailableException.Reason.OTHER);
        }
    }
}
