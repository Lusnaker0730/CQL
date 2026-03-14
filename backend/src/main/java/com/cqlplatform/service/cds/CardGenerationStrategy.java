package com.cqlplatform.service.cds;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.cds.CdsResponse;

/**
 * Strategy interface for generating CDS Hooks cards from CQL execution results.
 * Implementations convert CQL output into CDS cards using different approaches:
 * <ul>
 *   <li>{@link CqlTupleCardStrategy} — existing behavior: CQL Tuple-based cards</li>
 *   <li>{@link PlanDefinitionCardStrategy} — FHIR PlanDefinition action-based cards</li>
 * </ul>
 */
public interface CardGenerationStrategy {

    CdsResponse buildResponse(CdsHooksService.CdsServiceConfig config,
                              CqlExecutionResponse execResponse);
}
