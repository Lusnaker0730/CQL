-- Rollback V58: ehr_connection tenant isolation
ALTER TABLE ehr_connection DROP CONSTRAINT IF EXISTS fk_ehr_connection_tenant;
DROP INDEX IF EXISTS idx_ehr_connection_tenant;
ALTER TABLE ehr_connection DROP COLUMN IF EXISTS tenant_id;
-- Note: the 'default' tenant row is intentionally kept (may be referenced elsewhere).
