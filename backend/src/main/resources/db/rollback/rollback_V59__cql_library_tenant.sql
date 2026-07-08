-- Rollback V59: cql_library tenant foundation
ALTER TABLE cql_library DROP CONSTRAINT IF EXISTS fk_cql_library_tenant;
DROP INDEX IF EXISTS idx_cql_library_tenant;
ALTER TABLE cql_library DROP COLUMN IF EXISTS tenant_id;
-- Note: the 'default' tenant row is intentionally kept (may be referenced elsewhere).
