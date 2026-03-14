-- Rollback V17: Measure Lock and Setting

ALTER TABLE measure_definition DROP COLUMN IF EXISTS locked_by;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS locked_at;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS setting;
