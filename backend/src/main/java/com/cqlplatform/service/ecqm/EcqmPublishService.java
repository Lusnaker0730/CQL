package com.cqlplatform.service.ecqm;

import com.cqlplatform.entity.EcqmArtifactEntity;
import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.exception.CqlGenerationException;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.authoring.CqlBuildResult;
import com.cqlplatform.model.ecqm.EcqmConstants;
import com.cqlplatform.model.ecqm.PublishResult;
import com.cqlplatform.model.measure.*;
import com.cqlplatform.repository.EcqmArtifactRepository;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EcqmPublishService {

    private final EcqmArtifactRepository ecqmRepository;
    private final MeasureDefinitionRepository measureRepository;
    private final EcqmCqlBuilder ecqmCqlBuilder;
    private final EcqmCqlGenerationService cqlGenerationService;

    @SuppressWarnings("unchecked")
    @Transactional
    public PublishResult publish(Long artifactId, String currentUser) {
        EcqmArtifactEntity ecqm = ecqmRepository.findById(artifactId)
                .orElseThrow(() -> new ResourceNotFoundException("eCQM Artifact", artifactId));

        if (!ecqm.getOwnerUsername().equals(currentUser)) {
            throw new IllegalArgumentException("Only the owner can publish this artifact");
        }

        // Validate CQL via translation before publishing (risk 2.2: reject broken CQL)
        CqlTranslationResponse validation = cqlGenerationService.validateCql(artifactId);
        if (!validation.isSuccess()) {
            List<String> errorMessages = validation.getErrors() != null
                    ? validation.getErrors().stream()
                        .map(e -> String.format("Line %d:%d — %s", e.getStartLine(), e.getStartColumn(), e.getMessage()))
                        .toList()
                    : List.of("CQL translation failed");
            throw new CqlGenerationException(
                    "Cannot publish: CQL validation failed with " + errorMessages.size()
                    + " error(s):\n" + String.join("\n", errorMessages));
        }

        // Generate CQL
        CqlBuildResult buildResult = ecqmCqlBuilder.buildEcqmCql(
                ecqm.getName(), ecqm.getVersion(), ecqm.getScoringType(),
                ecqm.getPopulationBasis(), ecqm.getPopulationGroupsList(),
                ecqm.getBaseElementsList(), ecqm.getParametersList(),
                ecqm.getSupplementalDataList(), ecqm.getStratifiersList(), "R4");

        // Build group definitions for MeasureDefinition
        List<GroupDefinition> groupDefs = buildGroupDefinitions(
                ecqm.getScoringType(), ecqm.getPopulationBasis(), ecqm.getPopulationGroupsList());

        // Create or update MeasureDefinition
        MeasureDefinitionEntity measureDef;
        if (ecqm.getPublishedMeasureId() != null) {
            measureDef = measureRepository.findById(ecqm.getPublishedMeasureId())
                    .orElse(newMeasureDefinition(ecqm, currentUser));
        } else {
            measureDef = newMeasureDefinition(ecqm, currentUser);
        }

        measureDef.setName(ecqm.getName());
        measureDef.setVersion(ecqm.getVersion());
        measureDef.setTitle(ecqm.getName());
        measureDef.setDescription(ecqm.getDescription());
        measureDef.setStatus("active");
        measureDef.setScoringType(ecqm.getScoringType());
        measureDef.setCqlContent(buildResult.cql());
        // Persist the compiled ELM alongside the CQL so MeasureReportService can
        // record elmHash for provenance (PAT-095). Without this, publish would
        // leave elm_json null on measure_definition → report.elm_hash null →
        // audit can't verify semantic equivalence of the measure actually run.
        measureDef.setElmJson(validation.getElmJson());
        measureDef.setCqlLibraryId(ecqm.getName().replaceAll("[^a-zA-Z0-9_]", "_"));
        measureDef.setGroupDefinitionList(groupDefs);
        measureDef.setImprovementNotation(ecqm.getImprovementNotation());
        measureDef.setRationale(ecqm.getRationale());
        measureDef.setClinicalGuidance(ecqm.getClinicalGuidance());
        measureDef.setSteward(ecqm.getSteward());
        measureDef.setDisclaimer(ecqm.getDisclaimer());
        measureDef.setCopyright(ecqm.getCopyright());
        measureDef.setMeasureSet(ecqm.getMeasureSet());
        measureDef.setNqfNumber(ecqm.getNqfNumber());
        measureDef.setCmsMeasureId(ecqm.getCmsMeasureId());
        measureDef.setSupplementalDataGuidance(ecqm.getSupplementalDataGuidance());

        measureDef = measureRepository.save(measureDef);

        // Update ecqm artifact with published measure id
        ecqm.setPublishedMeasureId(measureDef.getId());
        ecqm.setStatus("active");
        ecqmRepository.save(ecqm);

        log.info("Published eCQM artifact {} → MeasureDefinition id={}", artifactId, measureDef.getId());

        return PublishResult.builder()
                .measureDefinitionId(measureDef.getId())
                .measureName(measureDef.getName())
                .cql(buildResult.cql())
                .message("eCQM artifact published successfully")
                .build();
    }

    private MeasureDefinitionEntity newMeasureDefinition(EcqmArtifactEntity ecqm, String currentUser) {
        return MeasureDefinitionEntity.builder()
                .createdBy(currentUser)
                .ownerUsername(currentUser)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<GroupDefinition> buildGroupDefinitions(
            String scoringType, String populationBasis,
            List<Map<String, Object>> populationGroups) {
        List<GroupDefinition> result = new ArrayList<>();
        if (populationGroups == null) return result;

        boolean multiGroup = populationGroups.size() > 1;

        for (int g = 0; g < populationGroups.size(); g++) {
            Map<String, Object> group = populationGroups.get(g);
            String suffix = multiGroup ? " " + (g + 1) : "";
            Map<String, Object> populations = (Map<String, Object>) group.get("populations");

            List<PopulationDefinition> popDefs = new ArrayList<>();

            // Check dual IP
            Map<String, Object> ipDenom = (Map<String, Object>) group.get("initialPopulationDenom");
            Map<String, Object> ipNumer = (Map<String, Object>) group.get("initialPopulationNumer");
            boolean dualIp = EcqmConstants.SCORING_RATIO.equals(scoringType)
                    && ipDenom != null && ipNumer != null;

            if (dualIp) {
                popDefs.add(PopulationDefinition.builder()
                        .populationType("initial-population")
                        .criteriaExpression(EcqmConstants.INITIAL_POPULATION_1 + suffix)
                        .description("Initial Population (Denominator path)")
                        .build());
                popDefs.add(PopulationDefinition.builder()
                        .populationType("initial-population")
                        .criteriaExpression(EcqmConstants.INITIAL_POPULATION_2 + suffix)
                        .description("Initial Population (Numerator path)")
                        .build());
            }

            if (populations != null) {
                for (String popKey : EcqmConstants.ALL_POPULATION_KEYS) {
                    if (dualIp && "initial-population".equals(popKey)) continue;
                    Object popTree = populations.get(popKey);
                    if (popTree == null) continue;

                    String defineName = EcqmConstants.POPULATION_KEY_TO_DEFINE.get(popKey);
                    if (defineName == null) continue;

                    popDefs.add(PopulationDefinition.builder()
                            .populationType(popKey)
                            .criteriaExpression(defineName + suffix)
                            .build());
                }
            }

            // Observations
            List<ObservationDefinition> obsDefs = new ArrayList<>();
            List<Map<String, Object>> observations = (List<Map<String, Object>>) group.get("observations");
            if (observations != null) {
                for (Map<String, Object> obs : observations) {
                    String aggregateMethod = obs.get("aggregateMethod") != null
                            ? obs.get("aggregateMethod").toString().toLowerCase() : "count";
                    String popRef = obs.get("populationRef") != null
                            ? obs.get("populationRef").toString() : null;
                    obsDefs.add(ObservationDefinition.builder()
                            .criteriaExpression(EcqmConstants.MEASURE_OBSERVATION + suffix)
                            .aggregateMethod(aggregateMethod)
                            .populationRef(popRef)
                            .build());
                }
            }

            // Stratifiers
            List<StratifierDefinition> stratDefs = new ArrayList<>();
            List<Map<String, Object>> groupStratifiers = (List<Map<String, Object>>) group.get("stratifiers");
            if (groupStratifiers != null && !dualIp) {
                for (Map<String, Object> strat : groupStratifiers) {
                    String stratId = strat.get("stratifierId") != null
                            ? strat.get("stratifierId").toString() : "strat";
                    stratDefs.add(StratifierDefinition.builder()
                            .stratifierId(stratId)
                            .criteriaExpression("Stratifier " + stratId + suffix)
                            .build());
                }
            }

            String groupId = group.get("groupId") != null ? group.get("groupId").toString() : "group-" + (g + 1);

            Integer rateMultiplier = group.get("rateMultiplier") instanceof Number n ? n.intValue() : null;
            String scoringUnit = group.get("scoringUnit") != null ? group.get("scoringUnit").toString() : null;

            result.add(GroupDefinition.builder()
                    .groupId(groupId)
                    .description(group.get("description") != null ? group.get("description").toString() : null)
                    .populationBasis(populationBasis)
                    .populations(popDefs)
                    .observations(obsDefs)
                    .stratifiers(stratDefs)
                    .rateIndex(rateMultiplier)
                    .scoringUnit(scoringUnit)
                    .build());
        }

        return result;
    }
}
