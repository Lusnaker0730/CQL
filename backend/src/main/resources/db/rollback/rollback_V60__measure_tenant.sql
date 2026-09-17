-- Rollback V60: measure domain tenant foundation
ALTER TABLE measure_report DROP CONSTRAINT IF EXISTS fk_measure_report_tenant;
DROP INDEX IF EXISTS idx_measure_report_tenant;
ALTER TABLE measure_report DROP COLUMN IF EXISTS tenant_id;

ALTER TABLE ecqm_artifact DROP CONSTRAINT IF EXISTS fk_ecqm_artifact_tenant;
DROP INDEX IF EXISTS idx_ecqm_artifact_tenant;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS tenant_id;

ALTER TABLE measure_definition DROP CONSTRAINT IF EXISTS fk_measure_definition_tenant;
DROP INDEX IF EXISTS idx_measure_definition_tenant;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS tenant_id;
-- Note: the 'default' tenant row is intentionally kept.
