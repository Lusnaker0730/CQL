package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.CqlTranslationResponse.ExpressionInfo;
import com.cqlplatform.model.measure.*;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import com.cqlplatform.model.measure.ScoringTypeConstants;
import static com.cqlplatform.model.measure.PopulationTypeConstants.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureValidationService {

    private final MeasureDefinitionService definitionService;
    private final CqlTranslationService translationService;
    private final TestCaseService testCaseService;

    // Required populations per scoring type
    private static final Map<String, List<String>> REQUIRED_POPULATIONS = Map.of(
            ScoringTypeConstants.PROPORTION, List.of(INITIAL_POPULATION, DENOMINATOR, NUMERATOR),
            ScoringTypeConstants.RATIO, List.of(INITIAL_POPULATION, DENOMINATOR, NUMERATOR),
            ScoringTypeConstants.CONTINUOUS_VARIABLE, List.of(INITIAL_POPULATION, MEASURE_POPULATION),
            ScoringTypeConstants.COHORT, List.of(INITIAL_POPULATION),
            ScoringTypeConstants.COMPOSITE, List.of()
    );

    private static final Set<String> VALID_FHIR_RESOURCE_TYPES = Set.of(
            "Patient", "Encounter", "Condition", "Procedure", "Observation",
            "MedicationRequest", "MedicationAdministration", "MedicationDispense", "MedicationStatement",
            "DiagnosticReport", "ServiceRequest", "AllergyIntolerance", "Immunization",
            "CarePlan", "CareTeam", "Goal", "Coverage", "Claim", "ClaimResponse",
            "Communication", "CommunicationRequest", "Device", "DeviceRequest",
            "FamilyMemberHistory", "Flag", "Location", "Medication",
            "NutritionOrder", "Organization", "Practitioner", "PractitionerRole",
            "RelatedPerson", "Specimen", "Substance", "Task",
            "AdverseEvent", "BodyStructure", "Consent", "DetectedIssue",
            "DocumentReference", "EpisodeOfCare", "ImagingStudy", "Media",
            "QuestionnaireResponse", "RiskAssessment", "SupplyDelivery", "SupplyRequest"
    );

    /**
     * Full validation: CQL + populations + metadata + test cases + QI-Core
     */
    public ValidationReport validateFull(Long measureId) {
        long start = System.currentTimeMillis();
        ValidationReport report = new ValidationReport();

        MeasureDefinition measure = definitionService.getById(measureId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));

        // 1. Metadata validation
        validateMetadata(measure, report);

        // 2. CQL validation
        CqlTranslationResponse cqlResult = validateCql(measure, report);

        // 3. Population validation
        validatePopulations(measure, cqlResult, report);

        // 4. Stratifier validation
        validateStratifiers(measure, cqlResult, report);

        // 5. Observation validation
        validateObservations(measure, cqlResult, report);

        // 6. Risk adjustment & supplemental data
        validateRiskAdjustmentAndSupplemental(measure, cqlResult, report);

        // 7. Unused expression warnings
        checkUnusedExpressions(measure, cqlResult, report);

        // 8. QI-Core basic checks
        validateQiCore(cqlResult, report);

        // 9. Test case sufficiency
        validateTestCases(measureId, report);

        report.finalize(System.currentTimeMillis() - start);
        return report;
    }

    /**
     * Quick validation: CQL + populations only (for pre-save checks)
     */
    public ValidationReport validateQuick(Long measureId) {
        long start = System.currentTimeMillis();
        ValidationReport report = new ValidationReport();

        MeasureDefinition measure = definitionService.getById(measureId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));

        validateMetadata(measure, report);
        CqlTranslationResponse cqlResult = validateCql(measure, report);
        validatePopulations(measure, cqlResult, report);

        report.finalize(System.currentTimeMillis() - start);
        return report;
    }

    // ===== Metadata Validation =====

    private void validateMetadata(MeasureDefinition measure, ValidationReport report) {
        if (measure.getName() == null || measure.getName().isBlank()) {
            report.addError("METADATA", "Measure name is required", "name", "Go to Details tab and enter a name");
        }
        if (measure.getVersion() == null || measure.getVersion().isBlank()) {
            report.addError("METADATA", "Measure version is required", "version", "Go to Details tab and enter a version");
        }
        if (measure.getScoringType() == null || measure.getScoringType().isBlank()) {
            report.addError("METADATA", "Scoring type is required", "scoringType", "Go to Details tab and select a scoring type");
        }
        if (measure.getTitle() == null || measure.getTitle().isBlank()) {
            report.addWarning("METADATA", "Measure title is recommended", "title", "Go to Details tab and enter a title");
        }
        if (measure.getDescription() == null || measure.getDescription().isBlank()) {
            report.addWarning("METADATA", "Measure description is recommended", "description", "Go to Details tab and enter a description");
        }
        if (measure.getSteward() == null || measure.getSteward().isBlank()) {
            report.addWarning("METADATA", "Measure steward is recommended for publication", "steward", "Go to Details tab and enter a steward");
        }
        if (measure.getRationale() == null || measure.getRationale().isBlank()) {
            report.addInfo("METADATA", "Rationale helps reviewers understand the measure's purpose", "rationale", "Go to Details tab and enter a rationale");
        }
    }

    // ===== CQL Validation =====

    private CqlTranslationResponse validateCql(MeasureDefinition measure, ValidationReport report) {
        if (measure.getCqlContent() == null || measure.getCqlContent().isBlank()) {
            report.addError("CQL", "CQL content is required", "cqlContent", "Go to CQL tab and write CQL logic");
            return null;
        }

        try {
            CqlTranslationRequest request = new CqlTranslationRequest();
            request.setCql(measure.getCqlContent());
            CqlTranslationResponse result = translationService.translate(request);

            if (!result.isSuccess()) {
                // Add CQL translation errors
                if (result.getErrors() != null) {
                    for (CqlTranslationResponse.CqlError error : result.getErrors()) {
                        String location = error.getStartLine() != null ? " (line " + error.getStartLine() + ")" : "";
                        report.addError("CQL", error.getMessage() + location, "cqlContent",
                                "Fix the CQL error in the CQL tab");
                    }
                }
            }

            // Add CQL warnings
            if (result.getWarnings() != null) {
                for (CqlTranslationResponse.CqlError warning : result.getWarnings()) {
                    String location = warning.getStartLine() != null ? " (line " + warning.getStartLine() + ")" : "";
                    report.addWarning("CQL", warning.getMessage() + location, "cqlContent", null);
                }
            }

            // Check library dependencies
            if (result.getMetadata() != null && result.getMetadata().getIncludes() != null) {
                for (String include : result.getMetadata().getIncludes()) {
                    // includes are formatted as "LibName version 'x.y.z'" from metadata
                    report.addInfo("CQL", "Includes library: " + include, "includes", null);
                }
            }

            return result;
        } catch (Exception e) {
            report.addError("CQL", "CQL translation failed: " + e.getMessage(), "cqlContent",
                    "Fix the CQL syntax in the CQL tab");
            return null;
        }
    }

    // ===== Population Validation =====

    private void validatePopulations(MeasureDefinition measure, CqlTranslationResponse cqlResult, ValidationReport report) {
        List<GroupDefinition> groups = measure.getGroupDefinitions();

        if (groups == null || groups.isEmpty()) {
            report.addError("POPULATIONS", "At least one population group is required", "groupDefinitions",
                    "Go to Population Criteria tab and add a group");
            return;
        }

        Set<String> cqlExpressionNames = getCqlExpressionNames(cqlResult);
        Map<String, String> expressionReturnTypes = getCqlExpressionReturnTypes(cqlResult);
        String scoringType = measure.getScoringType() != null ? measure.getScoringType() : ScoringTypeConstants.PROPORTION;
        List<String> required = REQUIRED_POPULATIONS.getOrDefault(scoringType, List.of());

        for (int gi = 0; gi < groups.size(); gi++) {
            GroupDefinition group = groups.get(gi);
            String groupLabel = "Group " + (gi + 1);

            if (group.getPopulations() == null || group.getPopulations().isEmpty()) {
                report.addError("POPULATIONS", groupLabel + ": No populations defined", "groupDefinitions",
                        "Go to Population Criteria tab and add populations");
                continue;
            }

            // Check required populations exist
            Set<String> definedTypes = group.getPopulations().stream()
                    .map(PopulationDefinition::getPopulationType)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            for (String requiredType : required) {
                if (!definedTypes.contains(requiredType)) {
                    report.addError("POPULATIONS",
                            groupLabel + ": Required population '" + requiredType + "' is missing for " + scoringType + " scoring",
                            "groupDefinitions",
                            "Go to Population Criteria tab and add the missing population");
                }
            }

            // Check each population has an expression that exists in CQL
            for (PopulationDefinition pop : group.getPopulations()) {
                if (pop.getCriteriaExpression() == null || pop.getCriteriaExpression().isBlank()) {
                    if (required.contains(pop.getPopulationType())) {
                        report.addError("POPULATIONS",
                                groupLabel + ": Population '" + pop.getPopulationType() + "' has no CQL expression",
                                "groupDefinitions",
                                "Go to Population Criteria tab and select a CQL expression");
                    } else {
                        report.addWarning("POPULATIONS",
                                groupLabel + ": Optional population '" + pop.getPopulationType() + "' has no CQL expression",
                                "groupDefinitions", null);
                    }
                } else if (!cqlExpressionNames.isEmpty() && !cqlExpressionNames.contains(pop.getCriteriaExpression())) {
                    report.addError("POPULATIONS",
                            groupLabel + ": Expression '" + pop.getCriteriaExpression() + "' for population '" + pop.getPopulationType() + "' not found in CQL",
                            "groupDefinitions",
                            "Go to CQL tab and define the expression, or select a different one in Population Criteria");
                } else {
                    // Return type check
                    String basis = group.getPopulationBasis();
                    if ((basis == null || "Boolean".equalsIgnoreCase(basis)) && !cqlExpressionNames.isEmpty()) {
                        String returnType = expressionReturnTypes.get(pop.getCriteriaExpression());
                        if (returnType != null && !returnType.toLowerCase().contains("boolean")) {
                            report.addWarning("POPULATIONS",
                                    groupLabel + ": Expression '" + pop.getCriteriaExpression() + "' returns " + returnType + " but population basis expects Boolean",
                                    "groupDefinitions",
                                    "Change Population Basis or modify the CQL expression to return Boolean");
                        }
                    }
                }
            }

            // Ratio dual-IP: both must have different associationType
            if (ScoringTypeConstants.RATIO.equalsIgnoreCase(scoringType)) {
                List<PopulationDefinition> ips = group.getPopulations().stream()
                        .filter(p -> INITIAL_POPULATION.equals(p.getPopulationType()))
                        .collect(Collectors.toList());
                if (ips.size() == 2) {
                    String assoc1 = ips.get(0).getAssociationType();
                    String assoc2 = ips.get(1).getAssociationType();
                    if (assoc1 == null || assoc2 == null) {
                        report.addWarning("POPULATIONS",
                                groupLabel + ": Ratio with dual Initial Populations should have Denominator/Numerator association types",
                                "groupDefinitions", null);
                    } else if (assoc1.equals(assoc2)) {
                        report.addError("POPULATIONS",
                                groupLabel + ": Dual Initial Populations cannot have the same association type",
                                "groupDefinitions", null);
                    }
                }
            }
        }
    }

    // ===== Stratifier Validation =====

    private void validateStratifiers(MeasureDefinition measure, CqlTranslationResponse cqlResult, ValidationReport report) {
        if (measure.getGroupDefinitions() == null) return;
        Set<String> cqlExpressionNames = getCqlExpressionNames(cqlResult);

        for (int gi = 0; gi < measure.getGroupDefinitions().size(); gi++) {
            GroupDefinition group = measure.getGroupDefinitions().get(gi);
            String groupLabel = "Group " + (gi + 1);

            if (group.getStratifiers() == null) continue;

            for (StratifierDefinition strat : group.getStratifiers()) {
                if (strat.getCriteriaExpression() == null || strat.getCriteriaExpression().isBlank()) {
                    report.addWarning("POPULATIONS",
                            groupLabel + ": Stratifier '" + (strat.getStratifierId() != null ? strat.getStratifierId() : "unnamed") + "' has no CQL expression",
                            "groupDefinitions", "Go to Population Criteria tab and select an expression for the stratifier");
                } else if (!cqlExpressionNames.isEmpty() && !cqlExpressionNames.contains(strat.getCriteriaExpression())) {
                    report.addError("POPULATIONS",
                            groupLabel + ": Stratifier expression '" + strat.getCriteriaExpression() + "' not found in CQL",
                            "groupDefinitions", "Define this expression in the CQL tab");
                }

                if (strat.getCriteriaExpression() != null && !strat.getCriteriaExpression().isBlank()
                        && (strat.getAssociations() == null || strat.getAssociations().isEmpty())) {
                    report.addWarning("POPULATIONS",
                            groupLabel + ": Stratifier has expression but no population associations",
                            "groupDefinitions", "Select associated populations in the Population Criteria tab");
                }
            }
        }
    }

    // ===== Observation Validation =====

    private void validateObservations(MeasureDefinition measure, CqlTranslationResponse cqlResult, ValidationReport report) {
        if (measure.getGroupDefinitions() == null) return;
        Set<String> cqlExpressionNames = getCqlExpressionNames(cqlResult);
        String scoringType = measure.getScoringType() != null ? measure.getScoringType() : ScoringTypeConstants.PROPORTION;

        boolean needsObservation = ScoringTypeConstants.CONTINUOUS_VARIABLE.equalsIgnoreCase(scoringType);

        for (int gi = 0; gi < measure.getGroupDefinitions().size(); gi++) {
            GroupDefinition group = measure.getGroupDefinitions().get(gi);
            String groupLabel = "Group " + (gi + 1);

            if (needsObservation) {
                if (group.getObservations() == null || group.getObservations().isEmpty()) {
                    report.addError("POPULATIONS",
                            groupLabel + ": Continuous variable scoring requires at least one observation definition",
                            "groupDefinitions", "Go to Population Criteria tab and add an observation");
                }
            }

            if (group.getObservations() != null) {
                for (ObservationDefinition obs : group.getObservations()) {
                    if (obs.getCriteriaExpression() == null || obs.getCriteriaExpression().isBlank()) {
                        report.addError("POPULATIONS",
                                groupLabel + ": Observation has no CQL expression",
                                "groupDefinitions", "Select a CQL expression for the observation");
                    } else if (!cqlExpressionNames.isEmpty() && !cqlExpressionNames.contains(obs.getCriteriaExpression())) {
                        report.addError("POPULATIONS",
                                groupLabel + ": Observation expression '" + obs.getCriteriaExpression() + "' not found in CQL",
                                "groupDefinitions", "Define this expression in the CQL tab");
                    }

                    if (obs.getAggregateMethod() == null || obs.getAggregateMethod().isBlank()) {
                        report.addError("POPULATIONS",
                                groupLabel + ": Observation has no aggregate method",
                                "groupDefinitions", "Select an aggregate method (count, sum, average, min, max, median)");
                    }
                }
            }
        }
    }

    // ===== Risk Adjustment & Supplemental Data =====

    private void validateRiskAdjustmentAndSupplemental(MeasureDefinition measure, CqlTranslationResponse cqlResult, ValidationReport report) {
        Set<String> cqlExpressionNames = getCqlExpressionNames(cqlResult);

        if (measure.getRiskAdjustments() != null) {
            for (MeasureDefinition.RiskAdjustmentDef ra : measure.getRiskAdjustments()) {
                if (ra.getDefinition() != null && !ra.getDefinition().isBlank()
                        && !cqlExpressionNames.isEmpty() && !cqlExpressionNames.contains(ra.getDefinition())) {
                    report.addError("METADATA",
                            "Risk adjustment expression '" + ra.getDefinition() + "' not found in CQL",
                            "riskAdjustments", "Define this expression in the CQL tab");
                }
            }
        }

        if (measure.getSupplementalData() != null) {
            for (MeasureDefinition.SupplementalDataDef sd : measure.getSupplementalData()) {
                if (sd.getDefinition() != null && !sd.getDefinition().isBlank()
                        && !cqlExpressionNames.isEmpty() && !cqlExpressionNames.contains(sd.getDefinition())) {
                    report.addError("METADATA",
                            "Supplemental data expression '" + sd.getDefinition() + "' not found in CQL",
                            "supplementalData", "Define this expression in the CQL tab");
                }
            }
        }
    }

    // ===== Unused Expressions =====

    private void checkUnusedExpressions(MeasureDefinition measure, CqlTranslationResponse cqlResult, ValidationReport report) {
        if (cqlResult == null || cqlResult.getMetadata() == null || cqlResult.getMetadata().getExpressions() == null) return;

        Set<String> usedExpressions = new HashSet<>();

        // Gather all used expressions from populations, stratifiers, observations, risk adj, supplemental
        if (measure.getGroupDefinitions() != null) {
            for (GroupDefinition group : measure.getGroupDefinitions()) {
                if (group.getPopulations() != null) {
                    group.getPopulations().forEach(p -> {
                        if (p.getCriteriaExpression() != null) usedExpressions.add(p.getCriteriaExpression());
                    });
                }
                if (group.getStratifiers() != null) {
                    group.getStratifiers().forEach(s -> {
                        if (s.getCriteriaExpression() != null) usedExpressions.add(s.getCriteriaExpression());
                    });
                }
                if (group.getObservations() != null) {
                    group.getObservations().forEach(o -> {
                        if (o.getCriteriaExpression() != null) usedExpressions.add(o.getCriteriaExpression());
                    });
                }
            }
        }
        if (measure.getRiskAdjustments() != null) {
            measure.getRiskAdjustments().forEach(r -> {
                if (r.getDefinition() != null) usedExpressions.add(r.getDefinition());
            });
        }
        if (measure.getSupplementalData() != null) {
            measure.getSupplementalData().forEach(s -> {
                if (s.getDefinition() != null) usedExpressions.add(s.getDefinition());
            });
        }

        for (ExpressionInfo expr : cqlResult.getMetadata().getExpressions()) {
            if ("Public".equals(expr.getAccessLevel()) && !usedExpressions.contains(expr.getName())) {
                report.addInfo("CQL",
                        "Expression '" + expr.getName() + "' is defined but not used by any population, stratifier, or supplemental data",
                        expr.getName(), null);
            }
        }
    }

    // ===== QI-Core Basic Checks =====

    private void validateQiCore(CqlTranslationResponse cqlResult, ValidationReport report) {
        if (cqlResult == null || cqlResult.getMetadata() == null) return;

        // Check that CQL uses FHIR model
        List<String> usings = cqlResult.getMetadata().getUsings();
        if (usings != null) {
            boolean hasFhir = usings.stream().anyMatch(u -> u.contains("FHIR") || u.contains("QICore"));
            if (!hasFhir) {
                report.addWarning("QI_CORE",
                        "CQL does not reference FHIR or QI-Core data model. eCQMs should use FHIR-based models.",
                        "usings", "Add 'using FHIR version \"4.0.1\"' to your CQL");
            }
        }

        // Check expressions for unknown resource types (basic heuristic from ELM)
        // This is a lightweight check; full QI-Core profile validation would need profile definitions
        if (cqlResult.getElmJson() != null) {
            for (String resourceType : extractRetrieveTypes(cqlResult.getElmJson())) {
                if (!VALID_FHIR_RESOURCE_TYPES.contains(resourceType)) {
                    report.addWarning("QI_CORE",
                            "CQL retrieves resource type '" + resourceType + "' which is not a standard FHIR resource type",
                            resourceType, "Verify the resource type name is correct");
                }
            }
        }
    }

    // ===== Test Case Sufficiency =====

    private void validateTestCases(Long measureId, ValidationReport report) {
        try {
            var testCases = testCaseService.getTestCasesForMeasure(measureId);
            if (testCases.isEmpty()) {
                report.addWarning("TEST_CASES", "No test cases defined. Add test cases to verify measure logic.",
                        "testCases", "Go to Test Cases tab and create test cases");
                return;
            }

            long passCount = testCases.stream()
                    .filter(tc -> "pass".equalsIgnoreCase(tc.getStatus()))
                    .count();
            long failCount = testCases.stream()
                    .filter(tc -> "fail".equalsIgnoreCase(tc.getStatus()))
                    .count();
            long untestedCount = testCases.size() - passCount - failCount;

            report.addInfo("TEST_CASES",
                    testCases.size() + " test cases: " + passCount + " pass, " + failCount + " fail, " + untestedCount + " untested",
                    "testCases", null);

            if (failCount > 0) {
                report.addWarning("TEST_CASES",
                        failCount + " test case(s) are failing",
                        "testCases", "Go to Test Cases tab and review failing tests");
            }

            if (testCases.size() < 2) {
                report.addWarning("TEST_CASES",
                        "Only " + testCases.size() + " test case(s). Consider adding more for better coverage.",
                        "testCases", "Go to Test Cases tab and add more test cases");
            }
        } catch (Exception e) {
            log.warn("Could not validate test cases for measure {}: {}", measureId, e.getMessage());
        }
    }

    // ===== Helpers =====

    private Set<String> getCqlExpressionNames(CqlTranslationResponse cqlResult) {
        if (cqlResult == null || cqlResult.getMetadata() == null || cqlResult.getMetadata().getExpressions() == null) {
            return Collections.emptySet();
        }
        return cqlResult.getMetadata().getExpressions().stream()
                .map(ExpressionInfo::getName)
                .collect(Collectors.toSet());
    }

    private Map<String, String> getCqlExpressionReturnTypes(CqlTranslationResponse cqlResult) {
        if (cqlResult == null || cqlResult.getMetadata() == null || cqlResult.getMetadata().getExpressions() == null) {
            return Collections.emptyMap();
        }
        return cqlResult.getMetadata().getExpressions().stream()
                .filter(e -> e.getResultType() != null)
                .collect(Collectors.toMap(ExpressionInfo::getName, ExpressionInfo::getResultType, (a, b) -> a));
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Extract resource type names from ELM JSON retrieve elements.
     * Looks for "dataType" fields with format "{http://hl7.org/fhir}ResourceType".
     */
    private List<String> extractRetrieveTypes(String elmJson) {
        List<String> types = new ArrayList<>();
        if (elmJson == null) return types;

        try {
            JsonNode root = MAPPER.readTree(elmJson);
            collectDataTypes(root, types);
        } catch (Exception e) {
            log.warn("Failed to parse ELM JSON for retrieve type extraction: {}", e.getMessage());
        }
        return types;
    }

    private void collectDataTypes(JsonNode node, List<String> types) {
        if (node == null) return;

        if (node.isObject()) {
            JsonNode dataType = node.get("dataType");
            if (dataType != null && dataType.isTextual()) {
                String value = dataType.asText();
                // Extract resource type from "{http://hl7.org/fhir}ResourceType"
                int braceEnd = value.indexOf('}');
                if (braceEnd >= 0 && braceEnd + 1 < value.length()) {
                    types.add(value.substring(braceEnd + 1));
                }
            }
            for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
                collectDataTypes(it.next(), types);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectDataTypes(child, types);
            }
        }
    }
}
