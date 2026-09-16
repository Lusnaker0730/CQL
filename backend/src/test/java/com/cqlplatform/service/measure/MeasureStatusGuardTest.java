package com.cqlplatform.service.measure;

import com.cqlplatform.exception.MeasureNotEvaluableException;
import com.cqlplatform.model.measure.MeasureDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PAT-219 — the lifecycle gate in front of measure evaluation. Locks the contract that
 * only {@code active} stored measures pass, that every other status (and a legacy null)
 * is refused with a 409-mapped exception carrying the id + status, and that ad-hoc
 * evaluations (no stored definition) are deliberately not gated.
 */
class MeasureStatusGuardTest {

    private static MeasureDefinition definition(Long id, String status) {
        return MeasureDefinition.builder().id(id).name("Guarded measure").status(status).build();
    }

    @Test
    void activeMeasure_isAllowed() {
        assertThatCode(() -> MeasureStatusGuard.requireEvaluable(42L, definition(42L, "active")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"draft", "in-review", "retired", "not-a-real-status"})
    void anyNonActiveStatus_isRefusedWithIdAndStatus(String status) {
        assertThatThrownBy(() -> MeasureStatusGuard.requireEvaluable(42L, definition(42L, status)))
                .isInstanceOf(MeasureNotEvaluableException.class)
                .hasMessageContaining("Measure 42")
                .hasMessageContaining("'" + status + "'")
                .hasMessageContaining("only 'active' measures")
                .satisfies(e -> {
                    MeasureNotEvaluableException ex = (MeasureNotEvaluableException) e;
                    assertThat(ex.getMeasureId()).isEqualTo(42L);
                    assertThat(ex.getStatus()).isEqualTo(status);
                });
    }

    @Test
    void nullStatus_isTreatedAsDraft() {
        // Legacy rows that never went through the lifecycle render as draft everywhere else
        // (dashboard counts, pending-review filter, HQMF export) — the guard must agree.
        assertThatThrownBy(() -> MeasureStatusGuard.requireEvaluable(42L, definition(42L, null)))
                .isInstanceOf(MeasureNotEvaluableException.class)
                .satisfies(e -> assertThat(((MeasureNotEvaluableException) e).getStatus()).isEqualTo("draft"));
    }

    @Test
    void idFallsBackToTheCallerSuppliedId_whenDefinitionCarriesNone() {
        assertThatThrownBy(() -> MeasureStatusGuard.requireEvaluable(7L, definition(null, "draft")))
                .isInstanceOf(MeasureNotEvaluableException.class)
                .satisfies(e -> assertThat(((MeasureNotEvaluableException) e).getMeasureId()).isEqualTo(7L));
    }

    @Test
    void adHocEvaluationWithoutStoredDefinition_isNotGated() {
        // Inline-CQL runs (/api/measures/evaluate, or a non-numeric measureId) have no
        // lifecycle: that is the explicit "run this CQL now" developer path.
        assertThatCode(() -> MeasureStatusGuard.requireEvaluable(null, null)).doesNotThrowAnyException();
        assertThatCode(() -> MeasureStatusGuard.requireEvaluable(5L, null)).doesNotThrowAnyException();
    }
}
