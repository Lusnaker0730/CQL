package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import com.cqlplatform.model.measure.TestCaseExpectedValues;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.cqlplatform.model.measure.PopulationTypeConstants.*;

/**
 * PAT-259: how a composite measure's test case is shaped. A composite has no CQL, so its test case runs every
 * component's CQL against the same patient bundle. The actual values then hold one group per component group,
 * named {@code component-<measureId>:<groupId>}, plus the {@link #SUMMARY_GROUP_ID} group that applies the
 * composite scoring method to this ONE patient:
 * <ul>
 *   <li>opportunity — the sums over the components (each denominator membership is one opportunity);</li>
 *   <li>all-or-nothing — in the denominator when in any component's effective denominator, in the numerator only
 *       when in the numerator of every such component;</li>
 *   <li>linear / weighted — denominator as above, and a measure observation = the share of eligible components
 *       whose numerator the patient reached (weighted by the component weights for {@code weighted}).</li>
 * </ul>
 * {@link #groupDefinitions} is what the expected-value validation checks against.
 */
public final class CompositeTestCases {

    public static final String SUMMARY_GROUP_ID = CompositeMeasureService.SUMMARY_GROUP_ID;

    private CompositeTestCases() {}

    /** One component's verdict for the patient, read from the component's FIRST group. */
    public record ComponentMembership(Long measureId, boolean inInitialPopulation, boolean inDenominator,
                                      boolean inNumerator, Double weight) {}

    public static String componentGroupId(Long measureId, String groupId) {
        return "component-" + measureId + ":" + groupId;
    }

    public static ComponentMembership membership(Long measureId, TestCaseExpectedValues values, Double weight) {
        if (values == null || values.getGroups() == null || values.getGroups().isEmpty()
                || values.getGroups().get(0).getPopulations() == null) {
            return new ComponentMembership(measureId, false, false, false, weight);
        }
        Map<String, Integer> pops = values.getGroups().get(0).getPopulations();
        int ip = pops.getOrDefault(INITIAL_POPULATION, 0);
        int den = pops.getOrDefault(DENOMINATOR, 0) - pops.getOrDefault(DENOMINATOR_EXCLUSION, 0);
        int num = pops.getOrDefault(NUMERATOR, 0);
        return new ComponentMembership(measureId, ip > 0, den > 0, num > 0, weight);
    }

    public static TestCaseExpectedValues.GroupValues summary(String method, List<ComponentMembership> components) {
        String m = method == null || method.isBlank() ? ScoringTypeConstants.COMPOSITE_OPPORTUNITY : method.trim().toLowerCase(java.util.Locale.ROOT);
        Map<String, Integer> populations = new LinkedHashMap<>();
        List<Double> observations = null;
        if (ScoringTypeConstants.COMPOSITE_OPPORTUNITY.equals(m)) {
            int ip = 0, den = 0, num = 0;
            for (ComponentMembership c : components) {
                if (c.inInitialPopulation()) ip++;
                if (c.inDenominator()) den++;
                if (c.inNumerator()) num++;
            }
            populations.put(INITIAL_POPULATION, ip);
            populations.put(DENOMINATOR, den);
            populations.put(NUMERATOR, num);
        } else {
            boolean anyIp = components.stream().anyMatch(ComponentMembership::inInitialPopulation);
            boolean anyDen = components.stream().anyMatch(ComponentMembership::inDenominator);
            populations.put(INITIAL_POPULATION, anyIp ? 1 : 0);
            populations.put(DENOMINATOR, anyDen ? 1 : 0);
            if (ScoringTypeConstants.COMPOSITE_ALL_OR_NOTHING.equals(m)) {
                boolean allMet = anyDen && components.stream().allMatch(c -> !c.inDenominator() || c.inNumerator());
                populations.put(NUMERATOR, allMet ? 1 : 0);
            } else {
                // linear / weighted: the patient's share of met component numerators
                double eligible = 0, met = 0;
                for (ComponentMembership c : components) {
                    if (!c.inDenominator()) continue;
                    double w = ScoringTypeConstants.COMPOSITE_WEIGHTED.equals(m) && c.weight() != null ? c.weight() : 1.0;
                    eligible += w;
                    if (c.inNumerator()) met += w;
                }
                if (eligible > 0) observations = List.of(met / eligible);
            }
        }
        return TestCaseExpectedValues.GroupValues.builder()
                .groupId(SUMMARY_GROUP_ID)
                .populations(populations)
                .observations(observations)
                .build();
    }

    /** The groups an expectation for this composite may name: each component group and the summary group. */
    public static List<GroupDefinition> groupDefinitions(MeasureDefinition composite, List<MeasureDefinition> components) {
        List<GroupDefinition> out = new ArrayList<>();
        for (MeasureDefinition component : components) {
            List<GroupDefinition> groups = component.getGroupDefinitions() == null ? List.of() : component.getGroupDefinitions();
            for (int i = 0; i < groups.size(); i++) {
                GroupDefinition g = groups.get(i);
                out.add(GroupDefinition.builder()
                        .groupId(componentGroupId(component.getId(), TestCaseService.effectiveGroupId(g, i)))
                        .populations(g.getPopulations())
                        .stratifiers(g.getStratifiers())
                        .build());
            }
        }
        String method = composite.getCompositeScoring() == null ? ScoringTypeConstants.COMPOSITE_OPPORTUNITY : composite.getCompositeScoring();
        List<PopulationDefinition> summaryPops = new ArrayList<>();
        summaryPops.add(PopulationDefinition.builder().populationType(INITIAL_POPULATION).build());
        summaryPops.add(PopulationDefinition.builder().populationType(DENOMINATOR).build());
        if (ScoringTypeConstants.COMPOSITE_OPPORTUNITY.equals(method) || ScoringTypeConstants.COMPOSITE_ALL_OR_NOTHING.equals(method)) {
            summaryPops.add(PopulationDefinition.builder().populationType(NUMERATOR).build());
        }
        out.add(GroupDefinition.builder().groupId(SUMMARY_GROUP_ID).populations(summaryPops).build());
        return out;
    }
}
