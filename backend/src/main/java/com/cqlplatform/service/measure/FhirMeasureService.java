package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.fhir.CqfmConstants;
import com.cqlplatform.model.measure.*;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.cql.DataRequirementExtractor;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.ElmDependencies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.cqlplatform.model.measure.PopulationTypeConstants.*;

/**
 * FHIR Measure import / export.
 *
 * <p>PAT-229: the export is built by {@link CqfmMeasureBuilder} (HL7 Quality Measure IG / CRMI)
 * and comes with a {@link MeasureExportConformance} report; the import reads back everything
 * the export writes, so a package exported by one installation can be imported — and
 * evaluated — by another.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FhirMeasureService {

    private final MeasureDefinitionService definitionService;
    private final CqlTranslationService translationService;
    private final DataRequirementExtractor dataRequirementExtractor;
    private final CqfmMeasureBuilder measureBuilder;
    private final CqfmLibraryBuilder libraryBuilder;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Everything one export produced; the bundle service reuses the translation and dependencies. */
    public record MeasureExport(MeasureDefinition definition, ObjectNode measure,
                                MeasureExportConformance conformance, String elmJson,
                                String libraryName, String libraryVersion,
                                ElmDependencies dependencies, List<DataRequirementInfo> dataRequirements) {}

    // ------------------------------------------------------------------ export

    public ObjectNode exportAsFhirMeasure(Long id) {
        return export(id).measure();
    }

    public MeasureExport export(Long id) {
        MeasureDefinition definition = definitionService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        MeasureExportConformance report = new MeasureExportConformance();

        String elmJson = null;
        String libraryName = null;
        String libraryVersion = null;
        if (definition.getCqlContent() != null && !definition.getCqlContent().isBlank()) {
            libraryName = definition.getName();
            libraryVersion = definition.getVersion();
            try {
                CqlTranslationRequest request = new CqlTranslationRequest();
                request.setCql(definition.getCqlContent());
                CqlTranslationResponse response = translationService.translate(request);
                if (response.isSuccess() && response.getElmJson() != null) {
                    elmJson = response.getElmJson();
                } else {
                    report.add(MeasureExportConformance.ERROR, "Library.content",
                            "The CQL does not translate, so the package has no ELM and no data requirements.");
                }
                if (response.getMetadata() != null && response.getMetadata().getLibraryId() != null) {
                    libraryName = response.getMetadata().getLibraryId();
                    libraryVersion = response.getMetadata().getLibraryVersion();
                }
            } catch (Exception e) {
                log.warn("CQL translation failed during FHIR export of measure {}: {}", id, e.getMessage());
                report.add(MeasureExportConformance.ERROR, "Library.content",
                        "The CQL could not be translated: " + e.getMessage());
            }
        }

        ElmDependencies deps = libraryBuilder.readDependencies(elmJson);
        List<DataRequirementInfo> requirements = List.of();
        if (elmJson != null) {
            try {
                requirements = dataRequirementExtractor.extract(elmJson);
            } catch (Exception e) {
                log.warn("Data requirement extraction failed for measure {}: {}", id, e.getMessage());
            }
        }

        JsonNode importedBase = readStoredJson(definition);
        // A measure that was imported from FHIR and has not been edited since is returned exactly
        // as it came in: the original may carry elements this platform does not model.
        if (importedBase != null && fingerprint(parseFhirMeasure(importedBase)).equals(fingerprint(definition))) {
            for (JsonNode profile : importedBase.path("meta").path("profile")) {
                report.getProfiles().add(profile.asText());
            }
            report.add(MeasureExportConformance.INFO, "Measure",
                    "Unchanged since it was imported; exported as the original resource.");
            // Status is the one thing that legitimately differs: an imported measure starts as a
            // draft here and moves through this installation's own review workflow.
            ObjectNode original = (ObjectNode) importedBase.deepCopy();
            original.put("status", CqfmMeasureBuilder.fhirStatus(definition.getStatus()));
            return new MeasureExport(definition, original, report, elmJson,
                    libraryName, libraryVersion, deps, requirements);
        }

        ObjectNode measure = measureBuilder.build(definition, libraryName, libraryVersion, deps, requirements,
                importedBase, report);
        return new MeasureExport(definition, measure, report, elmJson, libraryName, libraryVersion, deps, requirements);
    }

    private JsonNode readStoredJson(MeasureDefinition definition) {
        if (definition.getFhirMeasureJson() == null || definition.getFhirMeasureJson().isBlank()) return null;
        try {
            JsonNode node = MAPPER.readTree(definition.getFhirMeasureJson());
            return node.isObject() ? node : null;
        } catch (Exception e) {
            log.warn("Stored FHIR Measure JSON of measure {} is not readable; rebuilding from the definition", definition.getId());
            return null;
        }
    }

    /** The modelled content of a definition — equal fingerprints mean "nothing we export has changed". */
    private String fingerprint(MeasureDefinition d) {
        StringBuilder sb = new StringBuilder();
        sb.append(d.getName()).append('|').append(d.getVersion()).append('|').append(d.getTitle()).append('|')
          .append(d.getDescription()).append('|')
          .append(d.getScoringType()).append('|').append(d.getImprovementNotation()).append('|')
          .append(d.getRateAggregation()).append('|').append(d.getRationale()).append('|')
          .append(d.getClinicalGuidance()).append('|').append(d.getSteward()).append('|')
          .append(d.getCopyright()).append('|').append(d.getDisclaimer()).append('|')
          .append(d.getRiskAdjustmentDescription()).append('|').append(d.getNqfNumber()).append('|')
          .append(d.getCmsMeasureId());
        if (d.getGroupDefinitions() != null) {
            for (GroupDefinition g : d.getGroupDefinitions()) {
                sb.append("#G:").append(g.getDescription()).append(':').append(Objects.toString(g.getPopulationBasis(), "boolean"));
                if (g.getPopulations() != null) {
                    g.getPopulations().forEach(p -> sb.append("|P:").append(p.getPopulationType()).append('=').append(p.getCriteriaExpression()));
                }
                if (g.getObservations() != null) {
                    g.getObservations().forEach(o -> sb.append("|O:").append(o.getCriteriaExpression()).append('=')
                            .append(o.getAggregateMethod()).append('@').append(o.getPopulationRef()));
                }
                if (g.getStratifiers() != null) {
                    g.getStratifiers().forEach(s -> sb.append("|S:").append(s.getStratifierId()).append('=').append(s.getCriteriaExpression()));
                }
            }
        }
        if (d.getSupplementalData() != null) d.getSupplementalData().forEach(s -> sb.append("|SDE:").append(s.getDefinition()));
        if (d.getRiskAdjustments() != null) d.getRiskAdjustments().forEach(r -> sb.append("|RAF:").append(r.getDefinition()));
        return sb.toString();
    }

    // ------------------------------------------------------------------ import

    public MeasureDefinition importFhirMeasure(JsonNode fhirMeasureJson) {
        return importFhirMeasure(fhirMeasureJson, null);
    }

    /**
     * @param cqlContent CQL of the measure's primary library when it is known (bundle import);
     *                   without it the imported measure has populations but no logic to evaluate
     */
    public MeasureDefinition importFhirMeasure(JsonNode fhirMeasureJson, String cqlContent) {
        MeasureDefinition definition = parseFhirMeasure(fhirMeasureJson);
        if (cqlContent != null && !cqlContent.isBlank()) {
            definition.setCqlContent(cqlContent);
        }
        try {
            definition.setFhirMeasureJson(MAPPER.writeValueAsString(fhirMeasureJson));
        } catch (Exception e) {
            definition.setFhirMeasureJson(null);
        }
        return definitionService.create(definition);
    }

    /** Pure: reads a FHIR Measure into a definition without touching the database. */
    public MeasureDefinition parseFhirMeasure(JsonNode json) {
        String resourceType = json.path("resourceType").asText("");
        if (!"Measure".equals(resourceType)) {
            throw new IllegalArgumentException("Expected FHIR Measure resource, got: " + resourceType);
        }

        String scoringType = firstCode(json.path("scoring"), ScoringTypeConstants.PROPORTION);
        String rootBasis = extensionValue(json, CqfmConstants.EXT_POPULATION_BASIS, "valueCode");

        List<GroupDefinition> groups = new ArrayList<>();
        int groupIndex = 0;
        for (JsonNode groupNode : json.path("group")) {
            groupIndex++;
            // population id → type, to turn an observation's criteriaReference back into a population type
            Map<String, String> typeById = new HashMap<>();
            List<PopulationDefinition> populations = new ArrayList<>();
            List<JsonNode> observationNodes = new ArrayList<>();
            for (JsonNode popNode : groupNode.path("population")) {
                String popType = firstCode(popNode.path("code"), INITIAL_POPULATION);
                if (CqfmConstants.POPULATION_MEASURE_OBSERVATION.equals(popType)) {
                    observationNodes.add(popNode);
                    continue;
                }
                if (popNode.hasNonNull("id")) typeById.put(popNode.get("id").asText(), popType);
                populations.add(PopulationDefinition.builder()
                        .populationType(popType)
                        .criteriaExpression(popNode.path("criteria").path("expression").asText(""))
                        .description(popNode.path("description").asText(null))
                        .build());
            }

            List<ObservationDefinition> observations = new ArrayList<>();
            for (JsonNode obsNode : observationNodes) {
                String reference = extensionValue(obsNode, CqfmConstants.EXT_CRITERIA_REFERENCE, "valueString");
                observations.add(ObservationDefinition.builder()
                        .criteriaExpression(obsNode.path("criteria").path("expression").asText(""))
                        .aggregateMethod(extensionValue(obsNode, CqfmConstants.EXT_AGGREGATE_METHOD, "valueCode"))
                        .populationRef(reference != null ? typeById.getOrDefault(reference, reference) : null)
                        .description(obsNode.path("description").asText(null))
                        .build());
            }

            List<StratifierDefinition> stratifiers = new ArrayList<>();
            int stratIndex = 0;
            for (JsonNode stratNode : groupNode.path("stratifier")) {
                stratIndex++;
                String stratId = stratNode.path("code").path("text").asText(
                        stratNode.path("id").asText("stratifier-" + stratIndex));
                stratifiers.add(StratifierDefinition.builder()
                        .stratifierId(stratId)
                        .criteriaExpression(stratNode.path("criteria").path("expression").asText(""))
                        .description(stratNode.path("description").asText(null))
                        .build());
            }

            String basis = extensionValue(groupNode, CqfmConstants.EXT_POPULATION_BASIS, "valueCode");
            JsonNode unit = extensionNode(groupNode, CqfmConstants.EXT_SCORING_UNIT);
            String scoringUnit = unit == null ? null
                    : unit.path("valueCodeableConcept").path("text").asText(
                            firstCode(unit.path("valueCodeableConcept"), null));
            groups.add(GroupDefinition.builder()
                    .groupId(groupNode.path("id").asText("group-" + groupIndex))
                    .description(groupNode.path("description").asText(null))
                    .populationBasis(basis != null ? basis : rootBasis)
                    .populations(populations)
                    .observations(observations.isEmpty() ? null : observations)
                    .stratifiers(stratifiers)
                    .scoringUnit(scoringUnit)
                    .build());
        }

        String nqfNumber = null;
        String cmsMeasureId = null;
        for (JsonNode idNode : json.path("identifier")) {
            String idValue = idNode.path("value").asText(null);
            for (JsonNode coding : idNode.path("type").path("coding")) {
                String code = coding.path("code").asText("");
                if ("NQF".equals(code)) nqfNumber = idValue;
                else if ("CMS".equals(code)) cmsMeasureId = idValue;
            }
        }

        List<MeasureDefinition.SupplementalDataDef> supplementalData = new ArrayList<>();
        List<MeasureDefinition.RiskAdjustmentDef> riskAdjustments = new ArrayList<>();
        for (JsonNode sde : json.path("supplementalData")) {
            String define = sde.path("criteria").path("expression").asText(null);
            if (define == null || define.isBlank()) continue;
            String description = sde.path("description").asText(null);
            boolean riskFactor = false;
            for (JsonNode usage : sde.path("usage")) {
                if (CqfmConstants.USAGE_RISK_ADJUSTMENT_FACTOR.equals(firstCode(usage, null))) riskFactor = true;
            }
            if (riskFactor) {
                riskAdjustments.add(MeasureDefinition.RiskAdjustmentDef.builder().definition(define).description(description).build());
            } else {
                supplementalData.add(MeasureDefinition.SupplementalDataDef.builder().definition(define).description(description).build());
            }
        }

        List<String> developers = new ArrayList<>();
        for (JsonNode author : json.path("author")) {
            if (author.hasNonNull("name")) developers.add(author.get("name").asText());
        }
        List<MeasureDefinition.MeasureReference> references = new ArrayList<>();
        for (JsonNode artifact : json.path("relatedArtifact")) {
            String text = artifact.path("citation").asText(artifact.path("display").asText(artifact.path("url").asText(null)));
            if (text == null || text.isBlank()) continue;
            references.add(MeasureDefinition.MeasureReference.builder()
                    .type(artifact.path("type").asText("citation")).reference(text).build());
        }

        // Library canonical → library name: ".../Library/Name|1.0.0" → "Name"
        String libraryId = null;
        JsonNode library = json.path("library");
        if (library.isArray() && !library.isEmpty()) {
            libraryId = libraryNameOf(library.get(0).asText(""));
        }

        return MeasureDefinition.builder()
                .name(json.path("name").asText(json.path("id").asText("imported-measure")))
                .version(json.path("version").asText("1.0.0"))
                .title(json.path("title").asText(null))
                .description(json.path("description").asText(null))
                .status(platformStatus(json.path("status").asText(MeasureStatusConstants.DRAFT)))
                .scoringType(scoringType)
                .cqlLibraryId(libraryId)
                .nqfNumber(nqfNumber)
                .cmsMeasureId(cmsMeasureId)
                .improvementNotation(firstCode(json.path("improvementNotation"), null))
                .rateAggregation(json.path("rateAggregation").asText(null))
                .riskAdjustmentDescription(json.path("riskAdjustment").asText(null))
                .rationale(json.path("rationale").asText(null))
                .clinicalGuidance(json.path("guidance").asText(null))
                .steward(json.path("publisher").asText(null))
                .copyright(json.path("copyright").asText(null))
                .disclaimer(json.path("disclaimer").asText(null))
                .developers(developers.isEmpty() ? null : developers)
                .references(references.isEmpty() ? null : references)
                .supplementalData(supplementalData.isEmpty() ? null : supplementalData)
                .riskAdjustments(riskAdjustments.isEmpty() ? null : riskAdjustments)
                .groupDefinitions(groups)
                .build();
    }

    /** {@code https://x/fhir/Library/Foo|1.2.0} → {@code Foo}. */
    static String libraryNameOf(String canonical) {
        if (canonical == null || canonical.isBlank()) return null;
        String url = canonical.contains("|") ? canonical.substring(0, canonical.indexOf('|')) : canonical;
        return url.contains("/") ? url.substring(url.lastIndexOf('/') + 1) : url;
    }

    private static String platformStatus(String fhirStatus) {
        return switch (fhirStatus) {
            case "active" -> MeasureStatusConstants.ACTIVE;
            case "retired" -> MeasureStatusConstants.RETIRED;
            default -> MeasureStatusConstants.DRAFT;
        };
    }

    private static String firstCode(JsonNode codeableConcept, String fallback) {
        JsonNode coding = codeableConcept.path("coding");
        if (coding.isArray() && !coding.isEmpty()) {
            return coding.get(0).path("code").asText(fallback);
        }
        return fallback;
    }

    private static JsonNode extensionNode(JsonNode element, String url) {
        for (JsonNode ext : element.path("extension")) {
            if (url.equals(ext.path("url").asText())) return ext;
        }
        return null;
    }

    private static String extensionValue(JsonNode element, String url, String valueField) {
        JsonNode ext = extensionNode(element, url);
        return ext != null && ext.hasNonNull(valueField) ? ext.get(valueField).asText() : null;
    }
}
