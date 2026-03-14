package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureEvaluationRequest;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import com.cqlplatform.model.measure.EvaluationStatusConstants;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import static com.cqlplatform.model.measure.PopulationTypeConstants.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompositeMeasureService {

    private final MeasureDefinitionService definitionService;
    private final MeasureEvaluationService evaluationService;

    public MeasureEvaluationResult evaluateComposite(MeasureDefinition composite,
                                                      MeasureEvaluationRequest baseRequest) {
        List<Long> componentIds = composite.getComponentMeasureIds();
        if (componentIds == null || componentIds.isEmpty()) {
            throw new IllegalArgumentException("Composite measure has no component measures defined");
        }

        String compositeScoringType = composite.getCompositeScoring() != null
                ? composite.getCompositeScoring() : ScoringTypeConstants.COMPOSITE_OPPORTUNITY;

        List<MeasureEvaluationResult> componentResults = new ArrayList<>();
        for (Long componentId : componentIds) {
            MeasureDefinition component = definitionService.getById(componentId)
                    .orElseThrow(() -> new IllegalArgumentException("Component measure not found: " + componentId));

            MeasureEvaluationRequest componentRequest = new MeasureEvaluationRequest();
            componentRequest.setMeasureId(componentId.toString());
            componentRequest.setMeasureCql(component.getCqlContent());
            componentRequest.setPatientId(baseRequest.getPatientId());
            componentRequest.setPeriodStart(baseRequest.getPeriodStart());
            componentRequest.setPeriodEnd(baseRequest.getPeriodEnd());
            componentRequest.setFhirServerUrl(baseRequest.getFhirServerUrl());
            componentRequest.setReportType(baseRequest.getReportType());

            MeasureEvaluationResult componentResult = evaluationService.evaluateMeasure(
                    componentRequest, componentId, component);
            componentResults.add(componentResult);
        }

        return aggregateCompositeResults(composite, componentResults, compositeScoringType);
    }

    private MeasureEvaluationResult aggregateCompositeResults(
            MeasureDefinition composite,
            List<MeasureEvaluationResult> componentResults,
            String scoringType) {

        Double compositeScore;
        List<GroupResult> allGroups = new ArrayList<>();

        if (ScoringTypeConstants.COMPOSITE_LINEAR.equalsIgnoreCase(scoringType)) {
            // Linear: average of component scores
            double sum = 0;
            int count = 0;
            for (MeasureEvaluationResult result : componentResults) {
                if (result.getGroups() != null) {
                    for (GroupResult group : result.getGroups()) {
                        if (group.getMeasureScore() != null) {
                            sum += group.getMeasureScore();
                            count++;
                        }
                        allGroups.add(group);
                    }
                }
            }
            compositeScore = count > 0 ? sum / count : null;
        } else {
            // Opportunity: sum all numerators / sum all denominators
            int totalNumerator = 0;
            int totalDenominator = 0;
            for (MeasureEvaluationResult result : componentResults) {
                if (result.getGroups() != null) {
                    for (GroupResult group : result.getGroups()) {
                        if (group.getPopulations() != null) {
                            for (PopulationResult pop : group.getPopulations()) {
                                if (NUMERATOR.equals(pop.getPopulationType()) && pop.getCount() != null) {
                                    totalNumerator += pop.getCount();
                                }
                                if (DENOMINATOR.equals(pop.getPopulationType()) && pop.getCount() != null) {
                                    totalDenominator += pop.getCount();
                                }
                            }
                        }
                        allGroups.add(group);
                    }
                }
            }
            compositeScore = totalDenominator > 0 ? (double) totalNumerator / totalDenominator * 100 : null;
        }

        // Build composite summary group
        GroupResult compositeGroup = GroupResult.builder()
                .groupId("composite-summary")
                .description("Composite measure (" + scoringType + " scoring) - " + componentResults.size() + " components")
                .measureScore(compositeScore)
                .measureScoreUnit("percentage")
                .populations(List.of())
                .build();

        List<GroupResult> resultGroups = new ArrayList<>();
        resultGroups.add(compositeGroup);
        resultGroups.addAll(allGroups);

        MeasureEvaluationResult firstResult = componentResults.isEmpty() ? null : componentResults.get(0);

        return MeasureEvaluationResult.builder()
                .measureId(composite.getId() != null ? composite.getId().toString() : composite.getName())
                .measureName(composite.getName())
                .status(EvaluationStatusConstants.COMPLETE)
                .periodStart(firstResult != null ? firstResult.getPeriodStart() : null)
                .periodEnd(firstResult != null ? firstResult.getPeriodEnd() : null)
                .reportType(firstResult != null ? firstResult.getReportType() : "summary")
                .groups(resultGroups)
                .build();
    }
}
