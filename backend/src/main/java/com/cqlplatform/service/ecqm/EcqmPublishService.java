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
    private final com.cqlplatform.repository.TenantRepository tenantRepository;

    /** Effective tenant: the caller's, or the default tenant for legacy callers with none. */
    private Long effectiveTenantId() {
        Long tenantId = com.cqlplatform.security.TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return tenantRepository.findByCode("default")
                .map(com.cqlplatform.entity.TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public PublishResult publish(Long artifactId, String currentUser) {
        EcqmArtifactEntity ecqm = ecqmRepository.findByIdAndTenantId(artifactId, effectiveTenantId())
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
                ecqm.getScoringType(), ecqm.getPopulationBasis(), ecqm.getPopulationGroupsList(),
                ecqm.getStratifiersList());

        // Create or update MeasureDefinition
        MeasureDefinitionEntity measureDef;
        if (ecqm.getPublishedMeasureId() != null) {
            measureDef = measureRepository.findByIdAndTenantId(ecqm.getPublishedMeasureId(), effectiveTenantId())
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
        // PAT-236 standard metadata: copied only when the artifact has them, so a value the
        // author entered on the published measure survives a re-publish.
        if (ecqm.getMeasureTypeList() != null && !ecqm.getMeasureTypeList().isEmpty()) measureDef.setMeasureTypeList(new ArrayList<>(ecqm.getMeasureTypeList()));
        if (ecqm.getDefinitionTermList() != null && !ecqm.getDefinitionTermList().isEmpty()) measureDef.setDefinitionTermList(definitionTerms(ecqm.getDefinitionTermList()));
        if (ecqm.getClinicalRecommendationStatement() != null) measureDef.setClinicalRecommendationStatement(ecqm.getClinicalRecommendationStatement());
        if (ecqm.getEffectiveStart() != null) measureDef.setEffectiveStart(ecqm.getEffectiveStart());
        if (ecqm.getEffectiveEnd() != null) measureDef.setEffectiveEnd(ecqm.getEffectiveEnd());
        if (ecqm.getApprovalDate() != null) measureDef.setApprovalDate(ecqm.getApprovalDate());
        if (ecqm.getLastReviewDate() != null) measureDef.setLastReviewDate(ecqm.getLastReviewDate());
        if (ecqm.getExperimental() != null) measureDef.setExperimental(ecqm.getExperimental());
        // PAT-234: the workspace's SDE elements become the measure's declared supplemental
        // data / risk adjustment factors (by usage) — what the evaluation distributes and the
        // exchange package lists. Before, publish carried none of them.
        List<MeasureDefinition.SupplementalDataDef> sdeDefs = new ArrayList<>();
        List<MeasureDefinition.RiskAdjustmentDef> rafDefs = new ArrayList<>();
        splitSupplementalData(ecqm.getSupplementalDataList(), sdeDefs, rafDefs);
        measureDef.setSupplementalDataList(sdeDefs);
        measureDef.setRiskAdjustmentList(rafDefs);

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
                .tenantId(ecqm.getTenantId()) // published measure inherits the artifact's tenant
                .build();
    }

    /**
     * @param artifactStratifiers the artifact-level stratifiers (the eCQM workspace's
     *        "Stratifiers" tab). PAT-233: they apply to every group. Before, only group-level
     *        stratifiers were mapped — and no UI edits those — so a stratifier built in the
     *        workspace got its CQL define but never reached evaluation, reports or the
     *        exchange package.
     */
    @SuppressWarnings("unchecked")
    private List<GroupDefinition> buildGroupDefinitions(
            String scoringType, String populationBasis,
            List<Map<String, Object>> populationGroups,
            List<Map<String, Object>> artifactStratifiers) {
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

            // Stratifiers: group-level defines are suffixed per group ("Stratifier gender 1"),
            // artifact-level ones are emitted once, unsuffixed, and shared by every group.
            // Same dual-IP rule as the CQL builder, which skips both kinds.
            List<StratifierDefinition> stratDefs = new ArrayList<>();
            if (!dualIp) {
                List<Map<String, Object>> groupStratifiers = (List<Map<String, Object>>) group.get("stratifiers");
                if (groupStratifiers != null) {
                    for (Map<String, Object> strat : groupStratifiers) {
                        stratDefs.add(stratifierDefinition(strat, suffix));
                    }
                }
                if (artifactStratifiers != null) {
                    for (Map<String, Object> strat : artifactStratifiers) {
                        stratDefs.add(stratifierDefinition(strat, ""));
                    }
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

    /**
     * PAT-234 — each SDE element of the artifact by its {@code usage}: {@code risk-adjustment-factor}
     * elements become risk adjustment factors, everything else supplemental data. The define
     * name is the element's name, as the CQL builder emits it.
     */
    static void splitSupplementalData(List<Map<String, Object>> elements,
                                      List<MeasureDefinition.SupplementalDataDef> sdeDefs,
                                      List<MeasureDefinition.RiskAdjustmentDef> rafDefs) {
        if (elements == null) return;
        for (Map<String, Object> element : elements) {
            Object name = element.get("name");
            if (name == null || name.toString().isBlank()) continue;
            Object description = element.get("description");
            String desc = description != null && !description.toString().isBlank() ? description.toString() : null;
            if (com.cqlplatform.model.fhir.CqfmConstants.USAGE_RISK_ADJUSTMENT_FACTOR.equals(element.get("usage"))) {
                rafDefs.add(MeasureDefinition.RiskAdjustmentDef.builder().definition(name.toString().trim()).description(desc).build());
            } else {
                sdeDefs.add(MeasureDefinition.SupplementalDataDef.builder().definition(name.toString().trim()).description(desc).build());
            }
        }
    }

    /** PAT-236 — the artifact's {@code [{term, definition}]} maps as typed definition terms; blank terms are dropped. */
    static List<MeasureDefinition.DefinitionTerm> definitionTerms(List<Map<String, Object>> raw) {
        List<MeasureDefinition.DefinitionTerm> out = new ArrayList<>();
        for (Map<String, Object> entry : raw) {
            Object term = entry.get("term");
            Object definition = entry.get("definition");
            if ((term == null || term.toString().isBlank()) && (definition == null || definition.toString().isBlank())) continue;
            out.add(MeasureDefinition.DefinitionTerm.builder()
                    .term(term != null ? term.toString().trim() : null)
                    .definition(definition != null ? definition.toString().trim() : null)
                    .build());
        }
        return out;
    }

    /** The define the CQL builder emits for this stratifier, plus what the report needs to label it. */
    @SuppressWarnings("unchecked")
    private static StratifierDefinition stratifierDefinition(Map<String, Object> strat, String suffix) {
        String stratId = strat.get("stratifierId") != null ? strat.get("stratifierId").toString() : "strat";
        String kind = "value".equals(strat.get("kind")) ? StratifierDefinition.KIND_VALUE : StratifierDefinition.KIND_CRITERIA;
        Object description = strat.get("description");
        // PAT-235: a multi-component stratifier points at one define per component,
        // "Stratifier <id> <code><suffix>", the names the CQL builder emits.
        List<StratifierDefinition.Component> components = null;
        if (strat.get("components") instanceof List<?> list && !list.isEmpty()) {
            components = new ArrayList<>();
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> component) || component.get("code") == null) continue;
                String code = component.get("code").toString().trim();
                Object componentDescription = component.get("description");
                components.add(StratifierDefinition.Component.builder()
                        .code(code)
                        .criteriaExpression("Stratifier " + stratId + " " + code + suffix)
                        .kind("value".equals(component.get("kind")) ? StratifierDefinition.KIND_VALUE : StratifierDefinition.KIND_CRITERIA)
                        .description(componentDescription != null && !componentDescription.toString().isBlank()
                                ? componentDescription.toString() : null)
                        .build());
            }
        }
        return StratifierDefinition.builder()
                .stratifierId(stratId)
                .criteriaExpression(components != null ? null : "Stratifier " + stratId + suffix)
                .description(description != null && !description.toString().isBlank() ? description.toString() : null)
                .kind(kind)
                .components(components)
                .build();
    }
}
