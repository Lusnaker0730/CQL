package com.cqlplatform.service.measure;

import com.cqlplatform.model.fhir.CqfmConstants;
import com.cqlplatform.model.fhir.FhirCodeSystemConstants;
import com.cqlplatform.model.measure.DataRequirementInfo;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureExportConformance;
import com.cqlplatform.model.measure.MeasureStatusConstants;
import com.cqlplatform.model.measure.ObservationDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import com.cqlplatform.model.measure.StratifierDefinition;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.ElmDependencies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.cqlplatform.model.measure.MeasureExportConformance.ERROR;
import static com.cqlplatform.model.measure.MeasureExportConformance.INFO;
import static com.cqlplatform.model.measure.MeasureExportConformance.WARNING;

/**
 * Builds a FHIR R4 Measure that other organisations' tooling can read (PAT-229), following the
 * HL7 Quality Measure IG 5.0.0 and CRMI 2.0.0.
 *
 * <p>Two rules:
 * <ol>
 *   <li><b>Nothing is invented.</b> A profile is written to {@code meta.profile} only when the
 *       elements it requires are present in the definition. A missing title is reported, not
 *       replaced by the name; a missing improvement notation is reported, not defaulted to
 *       "increase" — that would assert clinical meaning the author never gave.</li>
 *   <li><b>Everything the platform knows is exported.</b> The previous export dropped the
 *       library reference, observations, supplemental data, risk adjustment, improvement
 *       notation and all narrative metadata.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class CqfmMeasureBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> IMPROVEMENT_NOTATIONS = Set.of("increase", "decrease");
    private static final Set<String> AGGREGATE_METHODS = Set.of("sum", "average", "median", "minimum", "maximum", "count");
    private static final Set<String> RELATED_ARTIFACT_TYPES = Set.of("documentation", "justification", "citation",
            "predecessor", "successor", "derived-from", "depends-on", "composed-of");
    private static final String CS_COMPOSITE_SCORING = "http://terminology.hl7.org/CodeSystem/composite-measure-scoring";

    /**
     * Root elements this builder does not model. When a measure was imported from FHIR and has
     * since been edited, they are carried over from the imported resource instead of being lost.
     */
    private static final List<String> PASS_THROUGH = List.of("subtitle", "experimental", "contact", "useContext",
            "jurisdiction", "purpose", "usage", "approvalDate", "lastReviewDate", "topic", "editor", "reviewer",
            "endorser", "subjectCodeableConcept", "subjectReference", "type", "clinicalRecommendationStatement",
            "definition");

    private final FhirCanonicalResolver canonical;
    private final CqfmLibraryBuilder libraryBuilder;

    /**
     * @param libraryName    name of the primary CQL library, or {@code null} when the measure has no CQL
     * @param deps           dependencies read from the primary library's ELM
     * @param importedBase   the FHIR Measure this definition was imported from, if any (may be {@code null})
     * @param report         receives the claimed profiles and every conformance gap
     */
    public ObjectNode build(MeasureDefinition def, String libraryName, String libraryVersion,
                            ElmDependencies deps, List<DataRequirementInfo> dataRequirements,
                            JsonNode importedBase, MeasureExportConformance report) {
        ObjectNode measure = MAPPER.createObjectNode();
        measure.put("resourceType", "Measure");
        measure.put("id", FhirCanonicalResolver.idPart(def.getName()));
        ObjectNode meta = measure.putObject("meta");
        measure.set("text", narrative(def));

        boolean hasLibrary = libraryName != null && !libraryName.isBlank();
        boolean hasRequirements = hasLibrary && (dataRequirements != null && !dataRequirements.isEmpty()
                || !deps.includes().isEmpty() || !deps.valueSets().isEmpty() || !deps.codeSystems().isEmpty());
        if (hasRequirements) {
            measure.putArray("contained").add(libraryBuilder.buildEffectiveDataRequirements(deps, dataRequirements));
            ObjectNode ext = measure.putArray("extension").addObject();
            ext.put("url", CqfmConstants.EXT_EFFECTIVE_DATA_REQUIREMENTS);
            ext.put("valueCanonical", "#" + CqfmConstants.EFFECTIVE_DATA_REQUIREMENTS_ID);
        }

        measure.put("url", canonical.measureUrl(def.getName()));
        addIdentifiers(measure, def);
        measure.put("version", notBlank(def.getVersion()) ? def.getVersion() : "1.0.0");
        measure.put("name", def.getName());
        if (notBlank(def.getTitle())) measure.put("title", def.getTitle());
        measure.put("status", fhirStatus(def.getStatus()));
        if (def.getUpdatedAt() != null) {
            measure.put("date", def.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        if (notBlank(def.getSteward())) measure.put("publisher", def.getSteward());
        if (notBlank(def.getDescription())) measure.put("description", def.getDescription());
        if (notBlank(def.getCopyright())) measure.put("copyright", def.getCopyright());
        // QM IG conformance requirement 3.4: a measure states the period it applies to.
        ObjectNode effectivePeriod = measure.putObject("effectivePeriod");
        effectivePeriod.put("start", def.getCreatedAt() != null
                ? def.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE)
                : java.time.LocalDate.now().withDayOfYear(1).toString());
        addAuthors(measure, def);
        addRelatedArtifacts(measure, def, report);
        if (hasLibrary) {
            // Only the primary library, as a canonical URL pinned to its version (requirement 3.2).
            measure.putArray("library").add(
                    FhirCanonicalResolver.versioned(canonical.libraryUrl(libraryName), libraryVersion));
        }
        if (notBlank(def.getDisclaimer())) measure.put("disclaimer", def.getDisclaimer());

        String scoring = notBlank(def.getScoringType()) ? def.getScoringType() : ScoringTypeConstants.PROPORTION;
        CqfmLibraryBuilder.codeable(measure.putObject("scoring"), FhirCodeSystemConstants.CS_MEASURE_SCORING, scoring, null);
        if (ScoringTypeConstants.COMPOSITE.equals(scoring) && notBlank(def.getCompositeScoring())) {
            CqfmLibraryBuilder.codeable(measure.putObject("compositeScoring"), CS_COMPOSITE_SCORING,
                    def.getCompositeScoring(), null);
        }
        if (notBlank(def.getRiskAdjustmentDescription())) measure.put("riskAdjustment", def.getRiskAdjustmentDescription());
        if (notBlank(def.getRateAggregation())) measure.put("rateAggregation", def.getRateAggregation());
        if (notBlank(def.getRationale())) measure.put("rationale", def.getRationale());
        boolean hasImprovementNotation = addImprovementNotation(measure, def, report);
        if (notBlank(def.getClinicalGuidance())) measure.put("guidance", def.getClinicalGuidance());

        GroupCheck groups = addGroups(measure, def, scoring, report);
        addSupplementalData(measure, def);
        passThrough(measure, importedBase);

        // ---- what does this resource actually conform to? ----
        boolean shareable = notBlank(def.getTitle()) && notBlank(def.getDescription());
        if (!notBlank(def.getTitle())) {
            report.add(WARNING, "Measure.title", "No title — required to be a shareable measure (CRMI).");
        }
        if (!notBlank(def.getDescription())) {
            report.add(WARNING, "Measure.description", "No description — required to be a shareable measure (CRMI).");
        }
        if (!hasLibrary) {
            report.add(ERROR, "Measure.library", "The measure has no CQL, so the package carries no logic and cannot be evaluated elsewhere.");
        }
        boolean cohort = ScoringTypeConstants.COHORT.equals(scoring);
        if (!hasImprovementNotation && !cohort) {
            report.add(WARNING, "Measure.improvementNotation",
                    "No improvement notation (increase / decrease) — required for a computable "
                            + scoring + " measure (invariant cmp-4).");
        }
        if (!canonical.isConfigured()) {
            report.add(WARNING, "Measure.url", "FHIR_CANONICAL_BASE is not configured: canonical URLs use the placeholder "
                    + canonical.getBase() + " and are not unique outside this installation.");
        }
        if (def.getName() != null && !def.getName().matches("[A-Z]([A-Za-z0-9_]){0,254}")) {
            report.add(INFO, "Measure.name", "Name is not a computer-friendly identifier (should match [A-Z][A-Za-z0-9_]*, invariant mea-0).");
        }
        if (ScoringTypeConstants.COMPOSITE.equals(scoring)) {
            report.add(INFO, "Measure.relatedArtifact", "Component measures of a composite are not packaged; export each component separately.");
        }

        boolean computable = hasLibrary && groups.valid && (hasImprovementNotation || cohort);
        List<String> profiles = new ArrayList<>();
        if (shareable) profiles.add(CqfmConstants.PROFILE_CRMI_SHAREABLE_MEASURE);
        if (shareable && MeasureStatusConstants.ACTIVE.equals(def.getStatus()) && def.getUpdatedAt() != null) {
            profiles.add(CqfmConstants.PROFILE_CRMI_PUBLISHABLE_MEASURE);
        }
        if (computable) {
            profiles.add(CqfmConstants.PROFILE_COMPUTABLE_MEASURE);
            String scoringProfile = scoringProfile(scoring);
            if (scoringProfile != null && groups.populationsMatchScoring) profiles.add(scoringProfile);
        }
        if (profiles.isEmpty()) {
            measure.remove("meta");
        } else {
            ArrayNode profileArray = meta.putArray("profile");
            profiles.forEach(profileArray::add);
        }
        report.setProfiles(profiles);
        report.setCanonicalBaseConfigured(canonical.isConfigured());
        return measure;
    }

    // ------------------------------------------------------------------ groups

    private record GroupCheck(boolean valid, boolean populationsMatchScoring) {}

    private GroupCheck addGroups(ObjectNode measure, MeasureDefinition def, String scoring,
                                 MeasureExportConformance report) {
        List<GroupDefinition> groups = def.getGroupDefinitions();
        if (groups == null || groups.isEmpty()) {
            report.add(ERROR, "Measure.group", "The measure defines no population group.");
            return new GroupCheck(false, false);
        }
        boolean valid = true;
        boolean populationsMatchScoring = true;
        ArrayNode groupArray = measure.putArray("group");
        for (int g = 0; g < groups.size(); g++) {
            GroupDefinition group = groups.get(g);
            String groupId = effectiveGroupId(group, g);
            ObjectNode groupNode = groupArray.addObject();
            groupNode.put("id", FhirCanonicalResolver.idPart(groupId));

            ArrayNode extensions = groupNode.putArray("extension");
            String basis = notBlank(group.getPopulationBasis()) ? group.getPopulationBasis() : "boolean";
            ObjectNode basisExt = extensions.addObject();
            basisExt.put("url", CqfmConstants.EXT_POPULATION_BASIS);
            basisExt.put("valueCode", basis);
            if (!"boolean".equalsIgnoreCase(basis)) {
                report.add(WARNING, "Measure.group[" + groupId + "]",
                        "Population basis is '" + basis + "'. This platform counts populations per patient, so an "
                                + "engine that honours the declared basis will count " + basis
                                + " events and can report different numbers than this platform does.");
            }
            if (notBlank(group.getScoringUnit())) {
                ObjectNode unitExt = extensions.addObject();
                unitExt.put("url", CqfmConstants.EXT_SCORING_UNIT);
                unitExt.putObject("valueCodeableConcept").put("text", group.getScoringUnit());
            }
            if (notBlank(group.getDescription())) groupNode.put("description", group.getDescription());

            // population type → id, so an observation can point at the population it is computed over
            Map<String, String> populationIds = new HashMap<>();
            Set<String> usedIds = new HashSet<>();
            List<String> populationTypes = new ArrayList<>();
            ArrayNode popArray = groupNode.putArray("population");
            if (group.getPopulations() == null || group.getPopulations().isEmpty()) {
                report.add(ERROR, "Measure.group[" + groupId + "].population", "The group has no population.");
                valid = false;
            } else {
                for (PopulationDefinition pop : group.getPopulations()) {
                    String type = pop.getPopulationType();
                    String id = uniqueId(usedIds, FhirCanonicalResolver.idPart(groupId + "-" + type));
                    populationIds.putIfAbsent(type, id);
                    populationTypes.add(type);
                    ObjectNode popNode = popArray.addObject();
                    popNode.put("id", id);
                    CqfmLibraryBuilder.codeable(popNode.putObject("code"),
                            FhirCodeSystemConstants.CS_MEASURE_POPULATION, type, null);
                    if (notBlank(pop.getDescription())) popNode.put("description", pop.getDescription());
                    if (!notBlank(pop.getCriteriaExpression())) {
                        report.add(ERROR, "Measure.group[" + groupId + "].population[" + type + "].criteria",
                                "Population has no criteria expression.");
                        valid = false;
                    }
                    criteria(popNode, pop.getCriteriaExpression());
                }
            }

            boolean hasObservation = false;
            if (group.getObservations() != null) {
                int n = 0;
                for (ObservationDefinition obs : group.getObservations()) {
                    n++;
                    hasObservation = true;
                    ObjectNode popNode = popArray.addObject();
                    popNode.put("id", uniqueId(usedIds, FhirCanonicalResolver.idPart(groupId + "-observation-" + n)));
                    ArrayNode obsExt = popNode.putArray("extension");
                    String method = obs.getAggregateMethod() != null
                            ? obs.getAggregateMethod().toLowerCase(Locale.ROOT) : null;
                    if (method != null && AGGREGATE_METHODS.contains(method)) {
                        ObjectNode e = obsExt.addObject();
                        e.put("url", CqfmConstants.EXT_AGGREGATE_METHOD);
                        e.put("valueCode", method);
                    } else {
                        report.add(WARNING, "Measure.group[" + groupId + "].observation[" + n + "]",
                                "Observation has no recognised aggregate method (sum, average, median, minimum, maximum, count).");
                    }
                    String referenced = populationIds.get(obs.getPopulationRef());
                    if (referenced != null) {
                        ObjectNode e = obsExt.addObject();
                        e.put("url", CqfmConstants.EXT_CRITERIA_REFERENCE);
                        e.put("valueString", referenced);
                    } else {
                        report.add(WARNING, "Measure.group[" + groupId + "].observation[" + n + "]",
                                "Observation refers to population '" + obs.getPopulationRef() + "', which this group does not have.");
                    }
                    if (obsExt.isEmpty()) popNode.remove("extension");
                    CqfmLibraryBuilder.codeable(popNode.putObject("code"),
                            FhirCodeSystemConstants.CS_MEASURE_POPULATION, CqfmConstants.POPULATION_MEASURE_OBSERVATION, null);
                    if (notBlank(obs.getDescription())) popNode.put("description", obs.getDescription());
                    criteria(popNode, obs.getCriteriaExpression());
                }
            }

            if (group.getStratifiers() != null && !group.getStratifiers().isEmpty()) {
                ArrayNode stratArray = groupNode.putArray("stratifier");
                for (StratifierDefinition strat : group.getStratifiers()) {
                    ObjectNode stratNode = stratArray.addObject();
                    stratNode.put("id", uniqueId(usedIds, FhirCanonicalResolver.idPart(groupId + "-" + strat.getStratifierId())));
                    stratNode.putObject("code").put("text", strat.getStratifierId());
                    if (notBlank(strat.getDescription())) stratNode.put("description", strat.getDescription());
                    if (strat.hasComponents()) {
                        // PAT-235: Measure.group.stratifier.component[] — one criteria per component,
                        // no stratifier-level criteria (the stratum is the combination).
                        ArrayNode componentArray = stratNode.putArray("component");
                        for (StratifierDefinition.Component component : strat.getComponents()) {
                            ObjectNode componentNode = componentArray.addObject();
                            componentNode.put("id", uniqueId(usedIds, FhirCanonicalResolver.idPart(
                                    groupId + "-" + strat.getStratifierId() + "-" + component.getCode())));
                            componentNode.putObject("code").put("text", component.getCode());
                            if (notBlank(component.getDescription())) componentNode.put("description", component.getDescription());
                            criteria(componentNode, component.getCriteriaExpression());
                        }
                    } else {
                        criteria(stratNode, strat.getCriteriaExpression());
                    }
                }
            }

            if (!requiredPopulationsPresent(scoring, populationTypes, hasObservation)) {
                populationsMatchScoring = false;
                report.add(WARNING, "Measure.group[" + groupId + "].population",
                        "Populations do not match what a " + scoring + " measure requires, so the "
                                + scoring + " profile is not claimed.");
            }
        }
        return new GroupCheck(valid, populationsMatchScoring);
    }

    /** Population types each scoring-type profile of the QM IG requires. */
    private boolean requiredPopulationsPresent(String scoring, List<String> types, boolean hasObservation) {
        return switch (scoring) {
            case ScoringTypeConstants.PROPORTION, ScoringTypeConstants.RATIO ->
                    types.contains("initial-population") && types.contains("denominator") && types.contains("numerator");
            case ScoringTypeConstants.CONTINUOUS_VARIABLE ->
                    types.contains("initial-population") && types.contains("measure-population") && hasObservation;
            case ScoringTypeConstants.COHORT -> types.contains("initial-population");
            default -> true;
        };
    }

    private String scoringProfile(String scoring) {
        return switch (scoring) {
            case ScoringTypeConstants.PROPORTION -> CqfmConstants.PROFILE_PROPORTION_MEASURE;
            case ScoringTypeConstants.RATIO -> CqfmConstants.PROFILE_RATIO_MEASURE;
            case ScoringTypeConstants.CONTINUOUS_VARIABLE -> CqfmConstants.PROFILE_CV_MEASURE;
            case ScoringTypeConstants.COHORT -> CqfmConstants.PROFILE_COHORT_MEASURE;
            case ScoringTypeConstants.COMPOSITE -> CqfmConstants.PROFILE_COMPOSITE_MEASURE;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ other elements

    private void addSupplementalData(ObjectNode measure, MeasureDefinition def) {
        ArrayNode array = MAPPER.createArrayNode();
        Set<String> usedIds = new HashSet<>();
        if (def.getSupplementalData() != null) {
            for (MeasureDefinition.SupplementalDataDef sde : def.getSupplementalData()) {
                if (!notBlank(sde.getDefinition())) continue;
                array.add(supplementalDatum(usedIds, "sde", sde.getDefinition(), sde.getDescription(),
                        CqfmConstants.USAGE_SUPPLEMENTAL_DATA));
            }
        }
        if (def.getRiskAdjustments() != null) {
            for (MeasureDefinition.RiskAdjustmentDef raf : def.getRiskAdjustments()) {
                if (!notBlank(raf.getDefinition())) continue;
                array.add(supplementalDatum(usedIds, "raf", raf.getDefinition(), raf.getDescription(),
                        CqfmConstants.USAGE_RISK_ADJUSTMENT_FACTOR));
            }
        }
        if (!array.isEmpty()) measure.set("supplementalData", array);
    }

    private ObjectNode supplementalDatum(Set<String> usedIds, String prefix, String define, String description, String usage) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", uniqueId(usedIds, FhirCanonicalResolver.idPart(prefix + "-" + define)));
        node.putObject("code").put("text", define);
        CqfmLibraryBuilder.codeable(node.putArray("usage").addObject(), CqfmConstants.CS_DATA_USAGE, usage, null);
        if (notBlank(description)) node.put("description", description);
        criteria(node, define);
        return node;
    }

    private boolean addImprovementNotation(ObjectNode measure, MeasureDefinition def, MeasureExportConformance report) {
        if (!notBlank(def.getImprovementNotation())) return false;
        String value = def.getImprovementNotation().toLowerCase(Locale.ROOT);
        if (!IMPROVEMENT_NOTATIONS.contains(value)) {
            report.add(WARNING, "Measure.improvementNotation",
                    "Improvement notation '" + def.getImprovementNotation() + "' is not increase / decrease and was not exported.");
            return false;
        }
        CqfmLibraryBuilder.codeable(measure.putObject("improvementNotation"), CqfmConstants.CS_IMPROVEMENT_NOTATION, value, null);
        return true;
    }

    private void addAuthors(ObjectNode measure, MeasureDefinition def) {
        if (def.getDevelopers() == null) return;
        ArrayNode authors = MAPPER.createArrayNode();
        for (String developer : def.getDevelopers()) {
            if (notBlank(developer)) authors.addObject().put("name", developer);
        }
        if (!authors.isEmpty()) measure.set("author", authors);
    }

    private void addRelatedArtifacts(ObjectNode measure, MeasureDefinition def, MeasureExportConformance report) {
        if (def.getReferences() == null) return;
        ArrayNode related = MAPPER.createArrayNode();
        for (MeasureDefinition.MeasureReference ref : def.getReferences()) {
            if (!notBlank(ref.getReference())) continue;
            String type = ref.getType() != null ? ref.getType().toLowerCase(Locale.ROOT) : "citation";
            if (!RELATED_ARTIFACT_TYPES.contains(type)) type = "citation";
            ObjectNode artifact = related.addObject();
            artifact.put("type", type);
            artifact.put("citation", ref.getReference());
        }
        if (!related.isEmpty()) measure.set("relatedArtifact", related);
    }

    private void addIdentifiers(ObjectNode measure, MeasureDefinition def) {
        ArrayNode identifiers = MAPPER.createArrayNode();
        if (notBlank(def.getNqfNumber())) {
            identifiers.add(identifier("urn:oid:2.16.840.1.113883.3.560", def.getNqfNumber(), "NQF", "NQF Number"));
        }
        if (notBlank(def.getCmsMeasureId())) {
            identifiers.add(identifier("https://madie.cms.gov/measure/id", def.getCmsMeasureId(), "CMS", "CMS Measure ID"));
        }
        if (!identifiers.isEmpty()) measure.set("identifier", identifiers);
    }

    private ObjectNode identifier(String system, String value, String typeCode, String typeDisplay) {
        ObjectNode id = MAPPER.createObjectNode();
        id.put("system", system);
        id.put("value", value);
        CqfmLibraryBuilder.codeable(id.putObject("type"), "http://terminology.hl7.org/CodeSystem/v2-0203", typeCode, typeDisplay);
        return id;
    }

    private void passThrough(ObjectNode measure, JsonNode importedBase) {
        if (importedBase == null || !importedBase.isObject()) return;
        for (String key : PASS_THROUGH) {
            if (importedBase.has(key) && !measure.has(key)) measure.set(key, importedBase.get(key).deepCopy());
        }
    }

    private void criteria(ObjectNode target, String expression) {
        ObjectNode criteria = target.putObject("criteria");
        criteria.put("language", CqfmConstants.LANGUAGE_CQL_IDENTIFIER);
        criteria.put("expression", expression != null ? expression : "");
    }

    /** draft | active | retired | unknown — the platform's "in-review" is still a draft to the outside. */
    static String fhirStatus(String platformStatus) {
        if (platformStatus == null) return "draft";
        return switch (platformStatus) {
            case MeasureStatusConstants.ACTIVE -> "active";
            case MeasureStatusConstants.RETIRED -> "retired";
            case MeasureStatusConstants.DRAFT, MeasureStatusConstants.IN_REVIEW -> "draft";
            default -> "unknown";
        };
    }

    /** Required by the QM IG (conformance requirement 3.1): a human-readable rendering of the measure. */
    private ObjectNode narrative(MeasureDefinition def) {
        StringBuilder html = new StringBuilder("<div xmlns=\"http://www.w3.org/1999/xhtml\">");
        html.append("<h2>").append(esc(notBlank(def.getTitle()) ? def.getTitle() : def.getName())).append("</h2>");
        if (notBlank(def.getDescription())) html.append("<p>").append(esc(def.getDescription())).append("</p>");
        html.append("<p><b>Scoring:</b> ").append(esc(def.getScoringType())).append("</p>");
        if (def.getGroupDefinitions() != null) {
            for (int g = 0; g < def.getGroupDefinitions().size(); g++) {
                GroupDefinition group = def.getGroupDefinitions().get(g);
                html.append("<h3>").append(esc(effectiveGroupId(group, g))).append("</h3><ul>");
                if (group.getPopulations() != null) {
                    for (PopulationDefinition pop : group.getPopulations()) {
                        html.append("<li>").append(esc(pop.getPopulationType())).append(": ")
                                .append(esc(pop.getCriteriaExpression())).append("</li>");
                    }
                }
                html.append("</ul>");
            }
        }
        html.append("</div>");
        ObjectNode text = MAPPER.createObjectNode();
        text.put("status", "generated");
        text.put("div", html.toString());
        return text;
    }

    /** Groups without an id are numbered the way the evaluation numbers them (group-1, group-2, …). */
    static String effectiveGroupId(GroupDefinition group, int index) {
        return group.getGroupId() != null && !group.getGroupId().isBlank()
                ? group.getGroupId() : "group-" + (index + 1);
    }

    private static String uniqueId(Set<String> used, String candidate) {
        String id = candidate;
        int n = 2;
        while (!used.add(id)) id = candidate + "-" + n++;
        return id;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
