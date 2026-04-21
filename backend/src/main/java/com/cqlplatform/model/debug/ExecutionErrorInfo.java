package com.cqlplatform.model.debug;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Structured error info shared by CDS invocation, CQL editor execute, and eCQM
 * measure evaluation. Presence in the debug response lets integrators classify
 * failures without regex-ing free-form message strings.
 *
 * <p>JSON wire shape is intentionally identical to the previous CDS-only
 * {@code CdsResponse.CdsErrorInfo} so existing CDS consumers don't observe a
 * schema change:
 * <pre>
 *   {
 *     "phase":              "cql_translation",
 *     "errorType":          "CqlExecutionException",
 *     "message":            "...",
 *     "stackTraceSummary":  ["com.cqlplatform...", ...]   // optional
 *   }
 * </pre>
 *
 * <p>Built via {@link com.cqlplatform.util.ExecutionErrorClassifier}; never
 * constructed by hand from catch blocks — that would bypass the shared
 * phase-classification heuristic and drift.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionErrorInfo {
    /** Lowercased phase enum name, e.g. "cql_translation". */
    private String phase;
    /** Simple class name of the root exception (or outermost if no cause chain). */
    private String errorType;
    /** Human-readable message from the exception. */
    private String message;
    /** Top stack frames filtered to com.cqlplatform.* — null if no own-package frames. */
    private List<String> stackTraceSummary;
}
