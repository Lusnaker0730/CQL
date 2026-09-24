-- Rollback V75: drop the standard Measure metadata columns (PAT-236).
--
-- Measure type, definition terms, clinical recommendation statement, effective / approval /
-- last-review dates and the experimental flag are lost; nothing else references them. Roll the
-- application back to a pre-PAT-236 image at the same time (ddl-auto: validate expects them).

ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS experimental;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS last_review_date;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS approval_date;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS effective_end;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS effective_start;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS clinical_recommendation_statement;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS definition_terms;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS measure_types;

ALTER TABLE measure_definition DROP COLUMN IF EXISTS experimental;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS last_review_date;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS approval_date;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS effective_end;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS effective_start;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS clinical_recommendation_statement;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS definition_terms;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS measure_types;
