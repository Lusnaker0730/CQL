package com.cqlplatform.service.cds;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.cds.CdsConstants;
import com.cqlplatform.model.cds.CdsResponse;
import com.cqlplatform.util.IdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Existing CQL Tuple-based card generation strategy.
 * Converts CQL execution results into CDS cards by:
 * <ul>
 *   <li>Detecting explicit card mode (expressions named "Card*")</li>
 *   <li>Parsing Tuple results with summary/detail/indicator fields into individual cards</li>
 *   <li>Aggregating non-Tuple results into a consolidated info card</li>
 *   <li>Handling SystemActions expressions</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CqlTupleCardStrategy implements CardGenerationStrategy {

    private final CdsValueFormatter valueFormatter;
    private final ObjectMapper objectMapper;

    @Override
    public CdsResponse buildResponse(CdsHooksService.CdsServiceConfig config,
                                     CqlExecutionResponse execResponse) {
        List<CdsResponse.Card> cards = new ArrayList<>();
        List<CdsResponse.SystemAction> systemActions = new ArrayList<>();

        log.info("Building cards from CQL execution. Success: {}, Results count: {}",
                execResponse.isSuccess(),
                execResponse.getResults() != null ? execResponse.getResults().size() : 0);

        String primaryMessage = null; // Detected from "CardSuggestion" etc.
        StringBuilder supplementaryDetail = new StringBuilder();
        String consolidatedIndicator = CdsConstants.INDICATOR_INFO;
        boolean hasExplicitCards = false;

        // Detect "explicit card mode": if any expression is named "Card" (case-insensitive prefix),
        // the CQL intentionally uses Tuple-based cards.
        boolean explicitCardMode = execResponse.getResults() != null
                && execResponse.getResults().keySet().stream()
                        .anyMatch(name -> name.equalsIgnoreCase("Card")
                                || name.toLowerCase().startsWith("card ")
                                || name.toLowerCase().startsWith("card:"));

        if (execResponse.getResults() != null) {
            for (Map.Entry<String, CqlExecutionResponse.ExpressionResult> entry : execResponse.getResults()
                    .entrySet()) {
                Object value = entry.getValue().getValue();
                log.info("Expression '{}': value={}, type={}",
                        entry.getKey(), value, entry.getValue().getValueType());

                // Handle SystemActions expression
                if ("SystemActions".equals(entry.getKey()) && value instanceof Iterable) {
                    systemActions.addAll(parseSystemActions(value));
                    continue;
                }

                if (value == null) {
                    continue;
                }

                String exprName = entry.getKey();

                // Tuple with CDS card fields → separate card
                if (CdsTupleAccessor.isTuple(value)) {
                    try {
                        String summary = CdsTupleAccessor.getString(value, "summary");
                        String detail = CdsTupleAccessor.getString(value, "detail");
                        String indicator = CdsTupleAccessor.getString(value, "indicator");
                        String sourceLabel = CdsTupleAccessor.getString(value, "sourceLabel");
                        String selectionBehavior = CdsTupleAccessor.getString(value, "selectionBehavior");
                        List<CdsResponse.Suggestion> suggestions = parseSuggestions(value);
                        List<CdsResponse.OverrideReason> overrideReasons = parseOverrideReasons(value);

                        if (summary != null) {
                            hasExplicitCards = true;
                            cards.add(CdsResponse.Card.builder()
                                    .uuid(IdGenerator.uuid())
                                    .summary(summary)
                                    .detail(detail)
                                    .indicator(indicator != null ? indicator : CdsConstants.INDICATOR_INFO)
                                    .source(CdsResponse.Source.builder()
                                            .label(sourceLabel != null ? sourceLabel : config.getTitle())
                                            .build())
                                    .selectionBehavior(selectionBehavior)
                                    .suggestions(suggestions.isEmpty() ? null : suggestions)
                                    .overrideReasons(overrideReasons.isEmpty() ? null : overrideReasons)
                                    .build());
                            continue;
                        }
                        // Tuple without summary: add all fields to supplementary detail
                        Map<String, Object> elements = CdsTupleAccessor.getElements(value);
                        if (!elements.isEmpty()) {
                            elements.forEach((k, v) -> {
                                if (supplementaryDetail.length() > 0)
                                    supplementaryDetail.append("\n");
                                supplementaryDetail.append("**").append(k).append("**: ")
                                        .append(valueFormatter.formatValue(v));
                            });
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse Tuple result for card", e);
                    }
                    continue;
                }

                // Detect primary message expression (CardSuggestion, Summary, etc.)
                String nameLower = exprName.toLowerCase();
                if (value instanceof String && primaryMessage == null
                        && (nameLower.contains("suggestion") || nameLower.contains("summary")
                                || nameLower.contains("message") || nameLower.contains("recommendation"))) {
                    primaryMessage = (String) value;
                    continue;
                }

                // Skip verbose FHIR Resource objects from consolidated detail
                if (value instanceof org.hl7.fhir.r4.model.Resource) {
                    log.debug("Skipping FHIR Resource '{}' from consolidated card detail", exprName);
                    continue;
                }

                // All other scalar types: append to supplementary detail
                String formattedLine = valueFormatter.formatExpressionLine(exprName, value);
                if (formattedLine != null) {
                    if (supplementaryDetail.length() > 0)
                        supplementaryDetail.append("\n");
                    supplementaryDetail.append(formattedLine);

                    if (value instanceof Boolean && (Boolean) value) {
                        consolidatedIndicator = config.getDefaultIndicator() != null
                                ? config.getDefaultIndicator()
                                : CdsConstants.INDICATOR_WARNING;
                    }
                }
            }
        }

        // Only build the consolidated card when no explicit Tuple cards were produced
        // and the CQL is not using explicit card mode.
        if (!hasExplicitCards && !explicitCardMode) {
            StringBuilder detail = new StringBuilder();
            if (primaryMessage != null) {
                detail.append(primaryMessage);
            }
            if (supplementaryDetail.length() > 0) {
                if (detail.length() > 0) detail.append("\n\n");
                detail.append(supplementaryDetail);
            }

            if (detail.length() > 0) {
                cards.add(CdsResponse.Card.builder()
                        .uuid(IdGenerator.uuid())
                        .summary(config.getTitle())
                        .detail(detail.toString())
                        .indicator(consolidatedIndicator)
                        .source(CdsResponse.Source.builder()
                                .label(config.getTitle())
                                .build())
                        .build());
            }
        }

        if (cards.isEmpty()) {
            cards.add(createInfoCard(config.getTitle(), CdsConstants.NO_RECOMMENDATIONS));
        }

        return CdsResponse.builder()
                .cards(cards)
                .systemActions(systemActions.isEmpty() ? null : systemActions)
                .build();
    }

    public CdsResponse.Card createInfoCard(String summary, String detail) {
        return CdsResponse.Card.builder()
                .uuid(IdGenerator.uuid())
                .summary(summary)
                .detail(detail)
                .indicator(CdsConstants.INDICATOR_INFO)
                .source(CdsResponse.Source.builder()
                        .label(CdsConstants.SOURCE_LABEL)
                        .build())
                .build();
    }

    public CdsResponse.Card createErrorCard(String errorMessage) {
        return CdsResponse.Card.builder()
                .uuid(IdGenerator.uuid())
                .summary(CdsConstants.ERROR_SUMMARY)
                .detail(CdsConstants.ERROR_DETAIL_PREFIX + errorMessage)
                .indicator(CdsConstants.INDICATOR_WARNING)
                .source(CdsResponse.Source.builder()
                        .label(CdsConstants.SOURCE_LABEL)
                        .build())
                .build();
    }

    private List<CdsResponse.Suggestion> parseSuggestions(Object cardTuple) {
        List<Object> suggestionsRaw = CdsTupleAccessor.getList(cardTuple, "suggestions");
        List<CdsResponse.Suggestion> suggestions = new ArrayList<>();

        for (Object suggObj : suggestionsRaw) {
            if (suggObj != null && suggObj.getClass().getSimpleName().contains("Tuple")) {
                String label = CdsTupleAccessor.getString(suggObj, "label");
                Boolean isRecommended = CdsTupleAccessor.getBoolean(suggObj, "isRecommended");
                List<CdsResponse.Action> actions = parseActions(suggObj);

                suggestions.add(CdsResponse.Suggestion.builder()
                        .uuid(IdGenerator.uuid())
                        .label(label)
                        .isRecommended(isRecommended)
                        .actions(actions.isEmpty() ? null : actions)
                        .build());
            }
        }

        return suggestions;
    }

    private List<CdsResponse.Action> parseActions(Object suggestionTuple) {
        List<Object> actionsRaw = CdsTupleAccessor.getList(suggestionTuple, "actions");
        List<CdsResponse.Action> actions = new ArrayList<>();

        for (Object actionObj : actionsRaw) {
            if (actionObj != null && actionObj.getClass().getSimpleName().contains("Tuple")) {
                String type = CdsTupleAccessor.getString(actionObj, "type");
                String description = CdsTupleAccessor.getString(actionObj, "description");
                String resourceJson = CdsTupleAccessor.getString(actionObj, "resource");
                String resourceId = CdsTupleAccessor.getString(actionObj, "resourceId");

                Object resource = null;
                if (resourceJson != null) {
                    try {
                        resource = objectMapper.readValue(resourceJson, Map.class);
                    } catch (Exception e) {
                        resource = resourceJson;
                    }
                }

                actions.add(CdsResponse.Action.builder()
                        .type(type)
                        .description(description)
                        .resource(resource)
                        .resourceId(resourceId)
                        .build());
            }
        }

        return actions;
    }

    private List<CdsResponse.OverrideReason> parseOverrideReasons(Object cardTuple) {
        List<Object> reasonsRaw = CdsTupleAccessor.getList(cardTuple, "overrideReasons");
        List<CdsResponse.OverrideReason> reasons = new ArrayList<>();

        for (Object reasonObj : reasonsRaw) {
            if (reasonObj != null && reasonObj.getClass().getSimpleName().contains("Tuple")) {
                String code = CdsTupleAccessor.getString(reasonObj, "code");
                String display = CdsTupleAccessor.getString(reasonObj, "display");
                String system = CdsTupleAccessor.getString(reasonObj, "system");

                reasons.add(CdsResponse.OverrideReason.builder()
                        .code(code != null ? CdsResponse.Coding.builder()
                                .system(system)
                                .code(code)
                                .display(display)
                                .build() : null)
                        .display(display)
                        .build());
            } else if (reasonObj instanceof String reasonStr) {
                reasons.add(CdsResponse.OverrideReason.builder()
                        .display(reasonStr)
                        .build());
            }
        }

        return reasons;
    }

    private List<CdsResponse.SystemAction> parseSystemActions(Object value) {
        List<CdsResponse.SystemAction> systemActions = new ArrayList<>();

        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && item.getClass().getSimpleName().contains("Tuple")) {
                    String type = CdsTupleAccessor.getString(item, "type");
                    String description = CdsTupleAccessor.getString(item, "description");
                    String resourceJson = CdsTupleAccessor.getString(item, "resource");
                    String resourceId = CdsTupleAccessor.getString(item, "resourceId");

                    Object resource = null;
                    if (resourceJson != null) {
                        try {
                            resource = objectMapper.readValue(resourceJson, Map.class);
                        } catch (Exception e) {
                            resource = resourceJson;
                        }
                    }

                    systemActions.add(CdsResponse.SystemAction.builder()
                            .type(type)
                            .description(description)
                            .resource(resource)
                            .resourceId(resourceId)
                            .build());
                }
            }
        }

        return systemActions;
    }
}
