-- Rollback for V51__measure_report_invariant_checks.sql
ALTER TABLE measure_report DROP CONSTRAINT IF EXISTS chk_measure_report_duration_nonneg;
ALTER TABLE measure_report DROP CONSTRAINT IF EXISTS chk_measure_report_patients_nonneg;
ALTER TABLE measure_report DROP CONSTRAINT IF EXISTS chk_measure_report_score_range;
ALTER TABLE measure_report DROP CONSTRAINT IF EXISTS chk_measure_report_period_order;
