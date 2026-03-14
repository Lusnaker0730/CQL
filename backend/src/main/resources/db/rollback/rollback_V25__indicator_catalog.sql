-- Rollback V25: Indicator Catalog

DROP TABLE IF EXISTS indicator_catalog CASCADE;

DROP INDEX IF EXISTS idx_measure_moh_code;

ALTER TABLE measure_definition DROP COLUMN IF EXISTS moh_indicator_code;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS nhia_p4p_code;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS drg_indicator_code;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS indicator_category;
