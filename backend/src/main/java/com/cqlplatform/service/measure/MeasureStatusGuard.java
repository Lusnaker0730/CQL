package com.cqlplatform.service.measure;

import com.cqlplatform.exception.MeasureNotEvaluableException;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureStatusConstants;
import lombok.extern.slf4j.Slf4j;

/**
 * PAT-219 — lifecycle gate in front of measure evaluation.
 *
 * <p>Only {@code active} stored measures may be evaluated against patient data. Draft,
 * in-review and retired logic has not (or no longer) passed review, so running it against a
 * live FHIR server risks acting on unvalidated clinical logic — wrong CDS alerts, wrong
 * rates, wrong dashboards. Unreviewed logic is verified through the test-case sandbox
 * ({@link TestCaseService}), which executes against an in-memory bundle and never touches
 * the production FHIR server.
 *
 * <p>Every path that evaluates a <em>stored</em> definition funnels through
 * {@link MeasureEvaluationService#evaluateMeasure(com.cqlplatform.model.measure.MeasureEvaluationRequest, Long, MeasureDefinition)}
 * (controller, batch, scheduled, composite components) plus
 * {@link CompositeMeasureService#evaluateComposite} for the composite parent — both call this
 * guard first, before any FHIR or CQL work starts.
 *
 * <p>An ad-hoc evaluation (inline CQL with no stored definition, {@code definition == null})
 * has no lifecycle and is deliberately not gated: that is the explicit "run this CQL now"
 * developer path, equivalent to the editor's execute action.
 */
@Slf4j
public final class MeasureStatusGuard {

    private MeasureStatusGuard() {
    }

    /**
     * @param measureDefinitionId id of the stored measure; only used for the error when the
     *                            definition itself carries no id
     * @param definition          the stored definition, or {@code null} for ad-hoc evaluation
     * @throws MeasureNotEvaluableException when a definition is present and not active
     */
    public static void requireEvaluable(Long measureDefinitionId, MeasureDefinition definition) {
        if (definition == null) {
            return;
        }
        String status = definition.getStatus();
        if (MeasureStatusConstants.ACTIVE.equals(status)) {
            return;
        }
        // A null status is a legacy row that never went through the lifecycle. Treat it as
        // draft — that is how the rest of the platform already renders it (dashboard counts,
        // pending-review filter, HQMF export).
        String effectiveStatus = status != null ? status : MeasureStatusConstants.DRAFT;
        Long id = definition.getId() != null ? definition.getId() : measureDefinitionId;
        log.warn("Rejected evaluation of measure {} ('{}'): status '{}' is not active",
                id, definition.getName(), effectiveStatus);
        throw new MeasureNotEvaluableException(id, effectiveStatus);
    }
}
