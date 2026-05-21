-- measure_report: add DB-level invariants that were previously only enforced in Java.
-- Rationale:
--   * Production dashboards/alerts trust measureScore / totalPatients / period_end >= period_start.
--     Today they're Java-enforced only; a buggy service path could write garbage and no one
--     would notice until the dashboard rendered nonsense.
--   * CHECK constraints fail the INSERT/UPDATE loudly instead of corrupting analytics.
-- Related: code-review issue #6 (PAT-075 / BUG-114).

-- Period end must not predate period start. A `period_end < period_start` row crashes
-- every downstream calculation (duration, overlap, rendering).
ALTER TABLE measure_report
    ADD CONSTRAINT chk_measure_report_period_order
        CHECK (period_end >= period_start);

-- Measure score should be a non-negative real. -1 used to be a "no data" sentinel but
-- NULL is now the convention. Cap the upper bound generously (continuous-variable
-- measures can legitimately exceed 100 for raw counts; 10000 guards against stray
-- garbage from serialization bugs without blocking legitimate data).
ALTER TABLE measure_report
    ADD CONSTRAINT chk_measure_report_score_range
        CHECK (measure_score IS NULL OR (measure_score >= 0 AND measure_score <= 10000));

-- Patient count is a population cardinality — negative or absurdly large means a bug.
ALTER TABLE measure_report
    ADD CONSTRAINT chk_measure_report_patients_nonneg
        CHECK (total_patients IS NULL OR total_patients >= 0);

-- Evaluation duration is a positive-or-zero millisecond count. Defensive only —
-- cheap to enforce since it's already a BIGINT.
ALTER TABLE measure_report
    ADD CONSTRAINT chk_measure_report_duration_nonneg
        CHECK (evaluation_duration_ms IS NULL OR evaluation_duration_ms >= 0);
