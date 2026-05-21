package com.cqlplatform.fhir;

import com.cqlplatform.exception.FhirServerUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;

import java.util.ArrayList;
import java.util.List;

/**
 * PAT-112c: HAPI FHIR servers sometimes signal failure by returning HTTP 200
 * with a {@link Bundle} whose entries contain an {@link OperationOutcome}
 * resource carrying {@code severity=error} or {@code severity=fatal}. Without
 * inspection, the platform treats these as valid (empty) data — indistinguishable
 * from "patient legitimately has no matching records" — and the silent-failure
 * anti-pattern PAT-112a eliminated at the exception level re-enters via the
 * success path.
 *
 * <p>This inspector walks a Bundle's top-level entries plus each entry's
 * {@code response.outcome} and throws {@link FhirServerUnavailableException}
 * the moment an error/fatal issue is found. Warnings and informational
 * OperationOutcomes are ignored (they are legitimate FHIR conformance signals,
 * e.g. "partial result truncated" for informational).
 *
 * <p>Call at every Bundle-consumption boundary:
 * {@link com.cqlplatform.service.fhir.FhirDataProviderService#bulkFetchAllPatients},
 * transaction responses, search responses.
 */
@Slf4j
public final class FhirErrorInspector {

    private FhirErrorInspector() {}

    /**
     * Scan a Bundle for embedded OperationOutcome errors. Throws if any fatal/error
     * issue found. No-op for null Bundles or for Bundles with only warnings/info.
     */
    public static void assertNoErrors(Bundle bundle, String contextHint) {
        if (bundle == null || !bundle.hasEntry()) return;

        List<String> errors = new ArrayList<>();
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource res = entry.getResource();
            if (res instanceof OperationOutcome oo) {
                collectFatalErrors(oo, errors);
            }
            if (entry.hasResponse() && entry.getResponse().hasOutcome()
                    && entry.getResponse().getOutcome() instanceof OperationOutcome respOo) {
                collectFatalErrors(respOo, errors);
            }
        }

        if (!errors.isEmpty()) {
            String message = "Upstream returned OperationOutcome error(s) during " + contextHint + ": "
                    + String.join("; ", errors);
            log.error(message);
            throw new FhirServerUnavailableException(message);
        }
    }

    private static void collectFatalErrors(OperationOutcome oo, List<String> sink) {
        for (OperationOutcome.OperationOutcomeIssueComponent issue : oo.getIssue()) {
            OperationOutcome.IssueSeverity severity = issue.getSeverity();
            if (severity == OperationOutcome.IssueSeverity.ERROR
                    || severity == OperationOutcome.IssueSeverity.FATAL) {
                String code = issue.getCode() != null ? issue.getCode().toCode() : "unknown";
                String detail = issue.getDiagnostics() != null && !issue.getDiagnostics().isBlank()
                        ? issue.getDiagnostics()
                        : (issue.getDetails() != null && issue.getDetails().getText() != null
                                ? issue.getDetails().getText()
                                : code);
                sink.add("[" + severity.toCode() + "/" + code + "] " + detail);
            }
        }
    }
}
