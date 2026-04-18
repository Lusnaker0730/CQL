package com.cqlplatform.model.authoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Catalog entry describing one modifier. Common across all modifier templates.
 *
 * <p>Template-specific configuration lives in nested typed blocks
 * ({@link DuringMeasurementPeriodConfig}, etc.) — NOT as flat fields on this class.
 * This keeps the shared POJO clean: adding a new structured modifier template means
 * adding a new nested config class + a matching nullable field here, rather than
 * scattering per-template fields across the top level.
 *
 * <p>Only ONE of the nested config fields should be non-null per entry, matching the
 * modifier's {@code cqlTemplate}. The engine dispatches based on {@code cqlTemplate}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifierDefinition {
    // ── Identity & dispatch ─────────────────────────────────────────────
    private String id;
    private String name;
    private String type;
    private List<String> inputTypes;
    private String returnType;
    private String cqlTemplate;
    private String cqlLibraryFunction;

    // ── User-editable values shown in the UI (input fields) ─────────────
    private Map<String, Object> values;
    private Map<String, Object> validator;
    private String comparisonOperator;

    /**
     * How this modifier transforms its list input. Controls whether it gets skipped in the
     * Measure Population path for continuous-variable (episode-based) measures, where the
     * Measure Observation function needs a resource list to iterate over.
     *
     * <p>Replaces the old string-matching heuristic on {@code cqlLibraryFunction} that
     * silently missed new modifier names (e.g. {@code C3F.AverageObservation} was not
     * caught — it would be applied in Measure Population and break the observation wrapper).
     *
     * <p>Values:
     * <ul>
     *   <li>{@code "preserves-list"} — modifier returns the same list shape (default; e.g. Verified, Confirmed, LookBack*)</li>
     *   <li>{@code "collapses-list"} — picks a single resource from the list (e.g. MostRecent, First)</li>
     *   <li>{@code "extracts-value"} — extracts a scalar / Quantity from a resource or aggregates a list into one (e.g. QuantityValue, AverageObservation)</li>
     * </ul>
     *
     * <p>Both {@code collapses-list} and {@code extracts-value} are skipped in CV Measure
     * Population; the distinction is for authoring-UI display and future logic, not the
     * current skip rule.
     */
    private String listBehavior;

    // ── Template-specific typed configs ─────────────────────────────────
    /**
     * Config block for {@code cqlTemplate = "DuringMeasurementPeriod"}.
     * Null for all other modifier templates. See class javadoc for rationale.
     */
    private DuringMeasurementPeriodConfig during;

    // =========================================================================
    // DuringMeasurementPeriod config
    // =========================================================================

    /**
     * Structured spec that drives CQL generation for {@code DuringMeasurementPeriod}
     * modifiers. Replaces what used to be JSON-embedded CQL string literals — the
     * engine generates a null-safe {@code case when X is T then ... end} expression
     * from this typed spec, making {@code FHIRHelpers.ToInterval(null)} dispatch
     * ambiguity unreachable by construction (BUG-110 / BUG-112 root-cause family).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DuringMeasurementPeriodConfig {
        /** CQL query alias used in the generated where clause (e.g. "O", "C", "E"). */
        private String alias;

        /** Primary date field + accepted FHIR choice types + optional fallback fields. */
        private DateFieldSpec dateFieldSpec;
    }

    /**
     * Describes a FHIR date field and its possible types. A modifier's where clause
     * tries each of {@link #types} in order, wrapped in a type-safe {@code is T} check,
     * then falls through to any {@link #fallbacks} before yielding {@code false}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateFieldSpec {
        /** FHIR property name (e.g. "effective", "onset", "period", "authoredOn"). */
        private String field;

        /**
         * FHIR types accepted at {@link #field}, in order of evaluation.
         * Supported: "Period", "dateTime", "instant".
         * Single-element list = non-choice field.
         */
        private List<String> types;

        /**
         * Optional fallback fields tried after the primary field's types are exhausted.
         * Example: Condition.onset (choice) with Condition.recordedDate (dateTime) fallback.
         */
        private List<FieldRef> fallbacks;
    }

    /** A specific field + type pair used for fallback chains. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldRef {
        private String field;
        /** Exactly one of "Period" / "dateTime" / "instant". */
        private String type;
    }
}
