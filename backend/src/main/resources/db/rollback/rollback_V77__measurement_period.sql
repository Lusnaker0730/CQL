-- Rollback V77: drop the Measurement Period columns (PAT-242).
--
-- The measures' and artifacts' measurement periods are lost; evaluation and test case runs fall
-- back to the caller-supplied / current-year period. Roll the application back to a pre-PAT-242
-- image at the same time (ddl-auto: validate expects the columns).

ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS measurement_period_end;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS measurement_period_start;

ALTER TABLE measure_definition DROP COLUMN IF EXISTS measurement_period_end;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS measurement_period_start;
