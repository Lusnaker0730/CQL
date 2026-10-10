-- PAT-242: the measure's own Measurement Period.
-- Until now the period came from whoever evaluated: the evaluate API caller, the current calendar
-- year for test case runs, and a hard-coded default in the generated CQL. A measure authored for
-- 2024 data therefore ran its test cases against 2026. Both the published measure and the eCQM
-- workspace artifact carry the period (publish copies artifact -> measure); null = not set, and the
-- previous defaults stay in force (no backfill).

ALTER TABLE measure_definition ADD COLUMN measurement_period_start DATE;
ALTER TABLE measure_definition ADD COLUMN measurement_period_end DATE;

ALTER TABLE ecqm_artifact ADD COLUMN measurement_period_start DATE;
ALTER TABLE ecqm_artifact ADD COLUMN measurement_period_end DATE;
