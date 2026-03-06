package com.cqlplatform.service.cds;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.cds.CdsConstants;
import com.cqlplatform.model.cds.CdsResponse;
import com.cqlplatform.util.IdGenerator;
import ca.uhn.fhir.context.FhirContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.hl7.fhir.r4.model.PlanDefinition.PlanDefinitionActionComponent;
import org.hl7.fhir.r4.model.TriggerDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates CDS Hooks cards from a FHIR PlanDefinition's actions.
 * Each PlanDefinition action maps to a CDS card when its applicability
 * condition (a CQL expression name) evaluates to true.
 *
 * <h3>PlanDefinition → CDS Card mapping:</h3>
 * <ul>
 *   <li>{@code action.title} → {@code card.summary}</li>
 *   <li>{@code action.description} → {@code card.detail} (static part)</li>
 *   <li>{@code action.condition[applicability].expression} → condition evaluated against CQL results</li>
 *   <li>{@code action.dynamicValue[].expression} → dynamic card content from CQL results</li>
 *   <li>{@code action.priority} → {@code card.indicator} (routine→info, urgent→warning, asap/stat→critical)</li>
 *   <li>{@code action.type} → {@code suggestion.action.type}</li>
 *   <li>{@code action.selectionBehavior} → {@code card.selectionBehavior}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanDefinitionCardStrategy implements CardGenerationStrategy {

    private final FhirContext fhirContext;
    private final CdsValueFormatter valueFormatter;

    @Override
    public CdsResponse buildResponse(CdsHooksService.CdsServiceConfig config,
                                     CqlExecutionResponse execResponse) {
        PlanDefinition planDef = parsePlanDefinition(config.getPlanDefinitionJson());
        if (planDef == null) {
            log.error("Failed to parse PlanDefinition JSON for service: {}", config.getId());
            return CdsResponse.builder()
                    .cards(List.of(CdsResponse.Card.builder()
                            .uuid(IdGenerator.uuid())
                            .summary(CdsConstants.ERROR_SUMMARY)
                            .detail("Invalid PlanDefinition configuration")
                            .indicator(CdsConstants.INDICATOR_WARNING)
                            .source(CdsResponse.Source.builder().label(config.getTitle()).build())
                            .build()))
                    .build();
        }

        List<CdsResponse.Card> cards = new ArrayList<>();
        Map<String, CqlExecutionResponse.ExpressionResult> results = execResponse.getResults();

        for (PlanDefinitionActionComponent action : planDef.getAction()) {
            if (!evaluateCondition(action, results)) {
                continue;
            }

            String detail = buildDetail(action, results);
            String indicator = mapPriority(action.getPriority());
            List<CdsResponse.Suggestion> suggestions = buildSuggestions(action);

            CdsResponse.Card card = CdsResponse.Card.builder()
                    .uuid(IdGenerator.uuid())
                    .summary(action.hasTitle() ? action.getTitle() : config.getTitle())
                    .detail(detail)
                    .indicator(indicator)
                    .source(CdsResponse.Source.builder()
                            .label(config.getTitle())
                            .build())
                    .selectionBehavior(action.hasSelectionBehavior()
                            ? action.getSelectionBehavior().toCode()
                            : null)
                    .suggestions(suggestions.isEmpty() ? null : suggestions)
                    .build();
            cards.add(card);
        }

        if (cards.isEmpty()) {
            cards.add(CdsResponse.Card.builder()
                    .uuid(IdGenerator.uuid())
                    .summary(config.getTitle())
                    .detail(CdsConstants.NO_RECOMMENDATIONS)
                    .indicator(CdsConstants.INDICATOR_INFO)
                    .source(CdsResponse.Source.builder().label(config.getTitle()).build())
                    .build());
        }

        return CdsResponse.builder().cards(cards).build();
    }

    private PlanDefinition parsePlanDefinition(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return (PlanDefinition) fhirContext.newJsonParser().parseResource(json);
        } catch (Exception e) {
            log.error("Failed to parse PlanDefinition JSON", e);
            return null;
        }
    }

    /**
     * Evaluate the action's applicability conditions against CQL results.
     * If no conditions are defined, the action is considered applicable.
     */
    private boolean evaluateCondition(PlanDefinitionActionComponent action,
                                      Map<String, CqlExecutionResponse.ExpressionResult> results) {
        if (!action.hasCondition()) {
            return true;
        }

        for (PlanDefinition.PlanDefinitionActionConditionComponent condition : action.getCondition()) {
            if (condition.getKind() != PlanDefinition.ActionConditionKind.APPLICABILITY) {
                continue;
            }
            if (!condition.hasExpression() || !condition.getExpression().hasExpression()) {
                continue;
            }

            String expressionName = condition.getExpression().getExpression();
            if (results == null || !results.containsKey(expressionName)) {
                log.debug("Condition expression '{}' not found in results, skipping action", expressionName);
                return false;
            }

            Object value = results.get(expressionName).getValue();
            if (value instanceof Boolean) {
                if (!(Boolean) value) {
                    return false;
                }
            } else if (value == null) {
                return false;
            }
            // Non-null, non-Boolean values are considered truthy
        }

        return true;
    }

    /**
     * Build the detail text for a card. Starts with the action's static description,
     * then appends any dynamicValue expressions resolved from CQL results.
     */
    private String buildDetail(PlanDefinitionActionComponent action,
                               Map<String, CqlExecutionResponse.ExpressionResult> results) {
        StringBuilder detail = new StringBuilder();

        if (action.hasDescription()) {
            detail.append(action.getDescription());
        }

        if (action.hasDynamicValue() && results != null) {
            for (PlanDefinition.PlanDefinitionActionDynamicValueComponent dynVal : action.getDynamicValue()) {
                if (!dynVal.hasExpression() || !dynVal.getExpression().hasExpression()) {
                    continue;
                }
                String exprName = dynVal.getExpression().getExpression();
                CqlExecutionResponse.ExpressionResult exprResult = results.get(exprName);
                if (exprResult != null && exprResult.getValue() != null) {
                    if (detail.length() > 0) {
                        detail.append("\n\n");
                    }
                    String path = dynVal.hasPath() ? dynVal.getPath() : exprName;
                    String formatted = valueFormatter.formatExpressionLine(path, exprResult.getValue());
                    if (formatted != null) {
                        detail.append(formatted);
                    }
                }
            }
        }

        return detail.length() > 0 ? detail.toString() : null;
    }

    /**
     * Map PlanDefinition action priority to CDS Hooks card indicator.
     */
    private String mapPriority(PlanDefinition.RequestPriority priority) {
        if (priority == null) {
            return CdsConstants.INDICATOR_INFO;
        }
        return switch (priority) {
            case ROUTINE -> CdsConstants.INDICATOR_INFO;
            case URGENT -> CdsConstants.INDICATOR_WARNING;
            case ASAP, STAT -> CdsConstants.INDICATOR_CRITICAL;
            default -> CdsConstants.INDICATOR_INFO;
        };
    }

    /**
     * Build suggestions from PlanDefinition sub-actions.
     */
    private List<CdsResponse.Suggestion> buildSuggestions(PlanDefinitionActionComponent action) {
        List<CdsResponse.Suggestion> suggestions = new ArrayList<>();

        if (!action.hasAction()) {
            // Single action without sub-actions: create a suggestion from the action itself
            if (action.hasType() && action.getType().hasCoding()) {
                String actionType = action.getType().getCodingFirstRep().getCode();
                suggestions.add(CdsResponse.Suggestion.builder()
                        .uuid(IdGenerator.uuid())
                        .label(action.hasTitle() ? action.getTitle() : actionType)
                        .actions(List.of(CdsResponse.Action.builder()
                                .type(actionType)
                                .description(action.hasDescription() ? action.getDescription() : null)
                                .build()))
                        .build());
            }
            return suggestions;
        }

        for (PlanDefinitionActionComponent subAction : action.getAction()) {
            String actionType = null;
            if (subAction.hasType() && subAction.getType().hasCoding()) {
                actionType = subAction.getType().getCodingFirstRep().getCode();
            }

            suggestions.add(CdsResponse.Suggestion.builder()
                    .uuid(IdGenerator.uuid())
                    .label(subAction.hasTitle() ? subAction.getTitle() : "Suggestion")
                    .actions(actionType != null ? List.of(CdsResponse.Action.builder()
                            .type(actionType)
                            .description(subAction.hasDescription() ? subAction.getDescription() : null)
                            .build()) : null)
                    .build());
        }

        return suggestions;
    }
}
