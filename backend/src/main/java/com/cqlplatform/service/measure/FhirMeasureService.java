package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.measure.*;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.cql.DataRequirementExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.cqlplatform.model.measure.PopulationTypeConstants.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FhirMeasureService {

    private final MeasureDefinitionService definitionService;
    private final CqlTranslationService translationService;
    private final DataRequirementExtractor dataRequirementExtractor;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public MeasureDefinition importFhirMeasure(JsonNode fhirMeasureJson) {
        String resourceType = fhirMeasureJson.path("resourceType").asText("");
        if (!"Measure".equals(resourceType)) {
            throw new IllegalArgumentException("Expected FHIR Measure resource, got: " + resourceType);
        }

        String name = fhirMeasureJson.path("name").asText(fhirMeasureJson.path("id").asText("imported-measure"));
        String version = fhirMeasureJson.path("version").asText("1.0.0");
        String title = fhirMeasureJson.path("title").asText(null);
        String description = fhirMeasureJson.path("description").asText(null);
        String status = fhirMeasureJson.path("status").asText(com.cqlplatform.model.measure.MeasureStatusConstants.DRAFT);

        // Extract scoring
        String scoringType = ScoringTypeConstants.PROPORTION;
        JsonNode scoring = fhirMeasureJson.path("scoring");
        if (scoring.has("coding") && scoring.get("coding").isArray() && scoring.get("coding").size() > 0) {
            scoringType = scoring.get("coding").get(0).path("code").asText(ScoringTypeConstants.PROPORTION);
        }

        // Extract groups
        List<GroupDefinition> groups = new ArrayList<>();
        JsonNode groupsNode = fhirMeasureJson.path("group");
        if (groupsNode.isArray()) {
            int groupIndex = 0;
            for (JsonNode groupNode : groupsNode) {
                groupIndex++;
                List<PopulationDefinition> populations = new ArrayList<>();
                JsonNode populationNode = groupNode.path("population");
                if (populationNode.isArray()) {
                    for (JsonNode popNode : populationNode) {
                        String popType = INITIAL_POPULATION;
                        JsonNode codePop = popNode.path("code");
                        if (codePop.has("coding") && codePop.get("coding").isArray()
                                && codePop.get("coding").size() > 0) {
                            popType = codePop.get("coding").get(0).path("code").asText(INITIAL_POPULATION);
                        }

                        String criteria = popNode.path("criteria").path("expression").asText("");
                        String popDesc = popNode.path("description").asText(null);

                        populations.add(PopulationDefinition.builder()
                                .populationType(popType)
                                .criteriaExpression(criteria)
                                .description(popDesc)
                                .build());
                    }
                }

                List<StratifierDefinition> stratifiers = new ArrayList<>();
                JsonNode stratifierNode = groupNode.path("stratifier");
                if (stratifierNode.isArray()) {
                    int stratIndex = 0;
                    for (JsonNode stratNode : stratifierNode) {
                        stratIndex++;
                        String stratId = stratNode.path("code").path("text").asText("stratifier-" + stratIndex);
                        String stratCriteria = stratNode.path("criteria").path("expression").asText("");
                        String stratDesc = stratNode.path("description").asText(null);

                        stratifiers.add(StratifierDefinition.builder()
                                .stratifierId(stratId)
                                .criteriaExpression(stratCriteria)
                                .description(stratDesc)
                                .build());
                    }
                }

                groups.add(GroupDefinition.builder()
                        .groupId("group-" + groupIndex)
                        .description(groupNode.path("description").asText(null))
                        .populations(populations)
                        .stratifiers(stratifiers)
                        .build());
            }
        }

        // Extract identifiers (NQF Number and CMS Measure ID)
        String nqfNumber = null;
        String cmsMeasureId = null;
        JsonNode identifiers = fhirMeasureJson.path("identifier");
        if (identifiers.isArray()) {
            for (JsonNode idNode : identifiers) {
                String idValue = idNode.path("value").asText(null);
                JsonNode typeCoding = idNode.path("type").path("coding");
                if (typeCoding.isArray()) {
                    for (JsonNode coding : typeCoding) {
                        String code = coding.path("code").asText("");
                        if ("NQF".equals(code)) {
                            nqfNumber = idValue;
                        } else if ("CMS".equals(code)) {
                            cmsMeasureId = idValue;
                        }
                    }
                }
            }
        }

        // Store raw FHIR JSON for round-trip fidelity
        String rawJson;
        try {
            rawJson = MAPPER.writeValueAsString(fhirMeasureJson);
        } catch (Exception e) {
            rawJson = null;
        }

        MeasureDefinition definition = MeasureDefinition.builder()
                .name(name)
                .version(version)
                .title(title)
                .description(description)
                .status(status)
                .scoringType(scoringType)
                .nqfNumber(nqfNumber)
                .cmsMeasureId(cmsMeasureId)
                .fhirMeasureJson(rawJson)
                .groupDefinitions(groups)
                .build();

        return definitionService.create(definition);
    }

    public ObjectNode exportAsFhirMeasure(Long id) {
        MeasureDefinition definition = definitionService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));

        // If we have stored FHIR JSON, return it for round-trip fidelity
        if (definition.getFhirMeasureJson() != null && !definition.getFhirMeasureJson().isBlank()) {
            try {
                return (ObjectNode) MAPPER.readTree(definition.getFhirMeasureJson());
            } catch (Exception e) {
                log.warn("Failed to parse stored FHIR Measure JSON, building from definition", e);
            }
        }

        // Build FHIR Measure resource from definition
        ObjectNode measure = MAPPER.createObjectNode();
        measure.put("resourceType", "Measure");
        measure.put("id", definition.getId().toString());
        measure.put("name", definition.getName());
        measure.put("version", definition.getVersion());
        measure.put("status", definition.getStatus() != null ? definition.getStatus() : com.cqlplatform.model.measure.MeasureStatusConstants.DRAFT);

        if (definition.getTitle() != null) {
            measure.put("title", definition.getTitle());
        }
        if (definition.getDescription() != null) {
            measure.put("description", definition.getDescription());
        }

        // Identifiers (NQF Number and CMS Measure ID)
        addIdentifiers(measure, definition);

        // Scoring
        ObjectNode scoring = measure.putObject("scoring");
        ArrayNode scoringCoding = scoring.putArray("coding");
        ObjectNode scoringCode = scoringCoding.addObject();
        scoringCode.put("system", com.cqlplatform.model.fhir.FhirCodeSystemConstants.CS_MEASURE_SCORING);
        scoringCode.put("code", definition.getScoringType() != null ? definition.getScoringType() : ScoringTypeConstants.PROPORTION);

        // Groups
        if (definition.getGroupDefinitions() != null && !definition.getGroupDefinitions().isEmpty()) {
            ArrayNode groupArray = measure.putArray("group");
            for (GroupDefinition group : definition.getGroupDefinitions()) {
                ObjectNode groupNode = groupArray.addObject();
                if (group.getDescription() != null) {
                    groupNode.put("description", group.getDescription());
                }

                if (group.getPopulations() != null && !group.getPopulations().isEmpty()) {
                    ArrayNode popArray = groupNode.putArray("population");
                    for (PopulationDefinition pop : group.getPopulations()) {
                        ObjectNode popNode = popArray.addObject();

                        ObjectNode code = popNode.putObject("code");
                        ArrayNode coding = code.putArray("coding");
                        ObjectNode codeEntry = coding.addObject();
                        codeEntry.put("system", com.cqlplatform.model.fhir.FhirCodeSystemConstants.CS_MEASURE_POPULATION);
                        codeEntry.put("code", pop.getPopulationType());

                        if (pop.getDescription() != null) {
                            popNode.put("description", pop.getDescription());
                        }

                        ObjectNode criteria = popNode.putObject("criteria");
                        criteria.put("language", "text/cql-identifier");
                        criteria.put("expression", pop.getCriteriaExpression());
                    }
                }

                if (group.getStratifiers() != null && !group.getStratifiers().isEmpty()) {
                    ArrayNode stratArray = groupNode.putArray("stratifier");
                    for (StratifierDefinition strat : group.getStratifiers()) {
                        ObjectNode stratNode = stratArray.addObject();

                        ObjectNode codeNode = stratNode.putObject("code");
                        codeNode.put("text", strat.getStratifierId());

                        if (strat.getDescription() != null) {
                            stratNode.put("description", strat.getDescription());
                        }

                        ObjectNode criteria = stratNode.putObject("criteria");
                        criteria.put("language", "text/cql-identifier");
                        criteria.put("expression", strat.getCriteriaExpression());
                    }
                }
            }
        }

        // Data Requirements
        addDataRequirements(measure, definition);

        return measure;
    }

    private void addIdentifiers(ObjectNode measure, MeasureDefinition definition) {
        boolean hasNqf = definition.getNqfNumber() != null && !definition.getNqfNumber().isBlank();
        boolean hasCms = definition.getCmsMeasureId() != null && !definition.getCmsMeasureId().isBlank();
        if (!hasNqf && !hasCms) {
            return;
        }

        ArrayNode identifiers = measure.putArray("identifier");
        if (hasNqf) {
            ObjectNode nqfId = identifiers.addObject();
            nqfId.put("system", "urn:oid:2.16.840.1.113883.3.560");
            nqfId.put("value", definition.getNqfNumber());
            ObjectNode nqfType = nqfId.putObject("type");
            ArrayNode nqfCoding = nqfType.putArray("coding");
            ObjectNode nqfCode = nqfCoding.addObject();
            nqfCode.put("system", "http://terminology.hl7.org/CodeSystem/v2-0203");
            nqfCode.put("code", "NQF");
            nqfCode.put("display", "NQF Number");
        }
        if (hasCms) {
            ObjectNode cmsId = identifiers.addObject();
            cmsId.put("system", "https://madie.cms.gov/measure/id");
            cmsId.put("value", definition.getCmsMeasureId());
            ObjectNode cmsType = cmsId.putObject("type");
            ArrayNode cmsCoding = cmsType.putArray("coding");
            ObjectNode cmsCode = cmsCoding.addObject();
            cmsCode.put("system", "http://terminology.hl7.org/CodeSystem/v2-0203");
            cmsCode.put("code", "CMS");
            cmsCode.put("display", "CMS Measure ID");
        }
    }

    private void addDataRequirements(ObjectNode measure, MeasureDefinition definition) {
        if (definition.getCqlContent() == null || definition.getCqlContent().isBlank()) {
            return;
        }
        try {
            CqlTranslationRequest request = new CqlTranslationRequest();
            request.setCql(definition.getCqlContent());
            CqlTranslationResponse response = translationService.translate(request);
            if (!response.isSuccess() || response.getElmJson() == null) {
                return;
            }

            List<DataRequirementInfo> requirements = dataRequirementExtractor.extract(response.getElmJson());
            if (requirements.isEmpty()) {
                return;
            }

            ArrayNode drArray = measure.putArray("dataRequirement");
            for (DataRequirementInfo dr : requirements) {
                ObjectNode drNode = drArray.addObject();
                drNode.put("type", dr.getType());

                if (dr.getProfile() != null && !dr.getProfile().isEmpty()) {
                    ArrayNode profileArray = drNode.putArray("profile");
                    for (String p : dr.getProfile()) {
                        profileArray.add(p);
                    }
                }

                if (dr.getCodeFilter() != null && !dr.getCodeFilter().isEmpty()) {
                    ArrayNode cfArray = drNode.putArray("codeFilter");
                    for (DataRequirementInfo.CodeFilterInfo cf : dr.getCodeFilter()) {
                        ObjectNode cfNode = cfArray.addObject();
                        if (cf.getPath() != null) {
                            cfNode.put("path", cf.getPath());
                        }
                        if (cf.getValueSet() != null) {
                            cfNode.put("valueSet", cf.getValueSet());
                        }
                        if (cf.getCode() != null && !cf.getCode().isEmpty()) {
                            ArrayNode codeArray = cfNode.putArray("code");
                            for (DataRequirementInfo.CodingInfo coding : cf.getCode()) {
                                ObjectNode codeNode = codeArray.addObject();
                                if (coding.getSystem() != null) codeNode.put("system", coding.getSystem());
                                if (coding.getCode() != null) codeNode.put("code", coding.getCode());
                                if (coding.getDisplay() != null) codeNode.put("display", coding.getDisplay());
                            }
                        }
                    }
                }

                if (dr.getDateFilter() != null && !dr.getDateFilter().isEmpty()) {
                    ArrayNode dfArray = drNode.putArray("dateFilter");
                    for (DataRequirementInfo.DateFilterInfo df : dr.getDateFilter()) {
                        ObjectNode dfNode = dfArray.addObject();
                        if (df.getPath() != null) {
                            dfNode.put("path", df.getPath());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to add data requirements to FHIR Measure export: {}", e.getMessage());
        }
    }
}
