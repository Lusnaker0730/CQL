package com.cqlplatform.model.authoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifierDefinition {
    private String id;
    private String name;
    private String type;
    private List<String> inputTypes;
    private String returnType;
    private String cqlTemplate;
    private String cqlLibraryFunction;
    private Map<String, Object> values;
    private Map<String, Object> validator;
    private String comparisonOperator;
    /** For DuringMeasurementPeriod: query alias (O/C/E/P/M/MS). */
    private String resourceAlias;
    /** For DuringMeasurementPeriod: pre-formed where clause using resourceAlias. */
    private String whereClause;

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
}
