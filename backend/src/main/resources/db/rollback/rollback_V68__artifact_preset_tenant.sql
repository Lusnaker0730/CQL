-- Rollback V68: drop tenant isolation from cds_artifact and sandbox_preset.
--
-- WARNING: this reopens the BUG-134 cross-tenant hole — with tenant_id gone, artifact and
-- preset lookups fall back to id-only and any ROLE_ADMIN (including a clinic tenant's admin)
-- can reach every tenant's rows again. Roll the application back to a pre-BUG-134 image at
-- the same time; the post-fix code requires the column (ddl-auto: validate will fail without it).
--
-- Rows created by non-default tenants after V68 keep their data but lose their tenant
-- attribution — that attribution is NOT recoverable from owner_username alone once a user
-- has been reassigned between tenants. Take a backup before running this.

ALTER TABLE sandbox_preset DROP CONSTRAINT IF EXISTS fk_sandbox_preset_tenant;
DROP INDEX IF EXISTS idx_sandbox_preset_tenant;
ALTER TABLE sandbox_preset DROP COLUMN IF EXISTS tenant_id;

ALTER TABLE cds_artifact DROP CONSTRAINT IF EXISTS fk_cds_artifact_tenant;
DROP INDEX IF EXISTS idx_cds_artifact_tenant;
ALTER TABLE cds_artifact DROP COLUMN IF EXISTS tenant_id;
