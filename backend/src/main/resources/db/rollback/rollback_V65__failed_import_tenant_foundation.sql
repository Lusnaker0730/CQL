-- Rollback V65: remove the failed_import tenant foundation.
DROP INDEX IF EXISTS idx_failed_import_tenant;
ALTER TABLE failed_import DROP CONSTRAINT IF EXISTS fk_failed_import_tenant;
ALTER TABLE failed_import DROP COLUMN IF EXISTS tenant_id;
