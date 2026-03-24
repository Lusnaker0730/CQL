-- Rollback V44: Remove elm_json from measure_definition
ALTER TABLE measure_definition DROP COLUMN IF EXISTS elm_json;
