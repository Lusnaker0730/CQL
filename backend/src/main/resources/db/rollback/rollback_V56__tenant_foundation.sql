-- Rollback V56: Tenant foundation
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS fk_app_user_tenant;
DROP INDEX IF EXISTS idx_app_user_tenant;
ALTER TABLE app_user DROP COLUMN IF EXISTS tenant_id;
DROP TABLE IF EXISTS tenant;
