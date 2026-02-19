package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.cqlplatform.exception.FhirServerUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.InstantType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FhirBulkExportService {

    private final FhirContext fhirContext;

    @CircuitBreaker(name = "fhirDataProvider", fallbackMethod = "kickOffExportFallback")
    @Retry(name = "fhirDataProvider")
    public BulkExportKickOffResult kickOffExport(String fhirServerUrl, String exportType,
                                                  String outputFormat, String since, String typeFilter) {
        log.info("Kicking off bulk export: type={}, server={}", exportType, fhirServerUrl);
        IGenericClient client = fhirContext.newRestfulGenericClient(fhirServerUrl);

        try {
            Parameters params = new Parameters();
            if (outputFormat != null && !outputFormat.isBlank()) {
                params.addParameter("_outputFormat", new StringType(outputFormat));
            }
            if (since != null && !since.isBlank()) {
                params.addParameter("_since", new InstantType(since));
            }
            if (typeFilter != null && !typeFilter.isBlank()) {
                params.addParameter("_type", new StringType(typeFilter));
            }

            Parameters result = client.operation()
                    .onServer()
                    .named("$export")
                    .withParameters(params)
                    .execute();

            // In practice, the server returns 202 with Content-Location header.
            // HAPI client may not expose headers directly, so we extract from result.
            String statusUrl = null;
            if (result.hasParameter("statusUrl")) {
                statusUrl = ((StringType) result.getParameter("statusUrl").getValue()).getValue();
            }

            return new BulkExportKickOffResult(statusUrl, exportType, Instant.now().toString());
        } catch (Exception e) {
            log.error("Bulk export kick-off failed", e);
            throw new FhirServerUnavailableException("Bulk export kick-off failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private BulkExportKickOffResult kickOffExportFallback(String fhirServerUrl, String exportType,
                                                          String outputFormat, String since, String typeFilter, Throwable t) {
        log.warn("Circuit breaker fallback for kickOffExport: {}", t.getMessage());
        throw new FhirServerUnavailableException("FHIR server unavailable for bulk export: " + t.getMessage(), t);
    }

    @CircuitBreaker(name = "fhirDataProvider", fallbackMethod = "pollExportStatusFallback")
    public BulkExportStatusResult pollExportStatus(String statusUrl) {
        log.info("Polling bulk export status: {}", statusUrl);

        try {
            IGenericClient client = fhirContext.newRestfulGenericClient(statusUrl);
            // Poll the status URL - this is typically a simple GET
            Parameters result = client.operation()
                    .onServer()
                    .named("$export-poll-status")
                    .withNoParameters(Parameters.class)
                    .execute();

            String status = com.cqlplatform.model.measure.EvaluationStatusConstants.COMPLETE;
            List<BulkExportOutput> outputs = new ArrayList<>();
            String errorMessage = null;
            int retryAfterSeconds = 0;

            for (var param : result.getParameter()) {
                switch (param.getName()) {
                    case "output": {
                        String type = null;
                        String url = null;
                        int count = 0;
                        for (var part : param.getPart()) {
                            switch (part.getName()) {
                                case "type":
                                    type = ((StringType) part.getValue()).getValue();
                                    break;
                                case "url":
                                    url = ((StringType) part.getValue()).getValue();
                                    break;
                                case "count":
                                    count = Integer.parseInt(((StringType) part.getValue()).getValue());
                                    break;
                                default:
                                    break;
                            }
                        }
                        outputs.add(new BulkExportOutput(type, url, count));
                        break;
                    }
                    case "error":
                        errorMessage = param.getValue() != null ? param.getValue().toString() : "Unknown error";
                        break;
                    case "retryAfter":
                        retryAfterSeconds = Integer.parseInt(((StringType) param.getValue()).getValue());
                        break;
                    default:
                        break;
                }
            }

            return new BulkExportStatusResult(status, retryAfterSeconds, outputs, errorMessage);
        } catch (Exception e) {
            log.error("Bulk export status poll failed", e);
            // Return in-progress if the poll itself fails with a retriable error
            return new BulkExportStatusResult(com.cqlplatform.model.measure.EvaluationStatusConstants.IN_PROGRESS, 10, List.of(), e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private BulkExportStatusResult pollExportStatusFallback(String statusUrl, Throwable t) {
        log.warn("Circuit breaker fallback for pollExportStatus: {}", t.getMessage());
        return new BulkExportStatusResult("error", 0, List.of(), "Server unavailable: " + t.getMessage());
    }

    public record BulkExportKickOffResult(String statusUrl, String exportType, String startedAt) {}
    public record BulkExportStatusResult(String status, int retryAfterSeconds, List<BulkExportOutput> output, String errorMessage) {}
    public record BulkExportOutput(String type, String url, int count) {}
}
