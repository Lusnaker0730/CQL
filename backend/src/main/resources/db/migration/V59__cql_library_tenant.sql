-- V59: Tenant foundation for cql_library (Phase 2 — foundation only; enforcement follows)
--
-- Adds tenant_id to cql_library and backfills existing rows to the default tenant. This is
-- an INERT foundation: reads are NOT yet tenant-scoped. tenant_id is kept NULLABLE and the
-- existing uq_library_name_version (name, version) is retained so nothing breaks.
--
-- The enforcement PR will: make tenant_id NOT NULL (after auditing all insert paths),
-- scope every read incl. DatabaseLibrarySourceProvider (with async tenant propagation via
-- TenantContext.callWith at the translation/execution executor sites), and change the
-- unique constraint to (tenant_id, name, version).

ALTER TABLE cql_library ADD COLUMN tenant_id BIGINT;

-- Ensure a default tenant exists (also created in V58; idempotent).
INSERT INTO tenant (code, name, active)
    SELECT 'default', 'Default Tenant', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE code = 'default');

-- Backfill every existing library to the default tenant.
UPDATE cql_library
    SET tenant_id = (SELECT id FROM tenant WHERE code = 'default')
    WHERE tenant_id IS NULL;

ALTER TABLE cql_library ADD CONSTRAINT fk_cql_library_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_cql_library_tenant ON cql_library(tenant_id);
