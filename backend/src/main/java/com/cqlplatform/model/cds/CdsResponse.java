package com.cqlplatform.model.cds;

import com.cqlplatform.model.CqlExecutionResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CdsResponse {
    private List<Card> cards;
    private List<SystemAction> systemActions;

    /** Only populated when the invocation request had debugMode=true. */
    private CdsDebugInfo debug;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CdsDebugInfo {
        /** CQL execution trace (expressions + FHIR retrieves). Null on early-phase failures. */
        private CqlExecutionResponse.DebugTrace debugTrace;

        /** Structured error info when invocation failed. Null on success. */
        private CdsErrorInfo error;

        /** Context snapshot — service id, hook, patientId, fhir server etc. */
        private Map<String, Object> invocationContext;

        /** Per-key prefetch resolution status (template resolution or sandbox testData). */
        private List<PrefetchStatus> prefetchStatus;

        /** Context/patient binding warnings (non-fatal). */
        private List<String> contextWarnings;

        /** FHIR server diagnostics when server-backed retrieve is attempted. */
        private FhirServerDiagnostics fhirServerDiagnostics;

        /** True when the invocation ran in dry-run mode (CQL execution skipped). */
        private Boolean dryRun;

        /** Resource count by FHIR type available to CQL engine (only populated in dry-run). */
        private Map<String, Integer> resourcesByType;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CdsErrorInfo {
        /** Phase in which the error occurred (see CdsInvocationException.Phase). */
        private String phase;
        /** Simple name of the root exception class. */
        private String errorType;
        private String message;
        /** Top N stack frames (own package preferred). */
        private List<String> stackTraceSummary;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PrefetchStatus {
        /** Prefetch key (e.g. "Patient", "Conditions"). */
        private String key;
        /** "success" / "failed" / "empty" */
        private String status;
        /** Resources parsed/resolved for this key. */
        private Integer count;
        /** Elapsed milliseconds for this key's resolution. */
        private Long elapsedMs;
        /** Error message if status=failed. */
        private String error;
        /** Resolved URL if this came from a template (null for sandbox testData). */
        private String resolvedUrl;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FhirServerDiagnostics {
        private String url;
        /** "not_attempted" / "success" / "error" */
        private String status;
        /** Error category: timeout / network / not_found / unauthorized / server_error / other */
        private String errorCategory;
        private String errorMessage;
        private Integer httpStatus;
        private Long elapsedMs;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Card {
        private String uuid;
        private String summary;
        private String detail;
        private String indicator; // info, warning, critical
        private Source source;
        private List<Suggestion> suggestions;
        private String selectionBehavior;
        private List<Link> links;
        private List<OverrideReason> overrideReasons;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Source {
        private String label;
        private String url;
        private String icon;
        private Coding topic;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Coding {
        private String system;
        private String code;
        private String display;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Suggestion {
        private String uuid;
        private String label;
        private Boolean isRecommended;
        private List<Action> actions;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Action {
        private String type; // create, update, delete
        private String description;
        private Object resource;
        private String resourceId;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Link {
        private String label;
        private String url;
        private String type; // absolute, smart
        private String appContext;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OverrideReason {
        private Coding code;
        private String display;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SystemAction {
        private String type;
        private String description;
        private Object resource;
        private String resourceId;
    }
}
