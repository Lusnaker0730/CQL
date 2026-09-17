-- V62: Enforce tenant isolation on cql_library (Phase 2 — hardening)
--
-- Completes the cql_library tenant work started in V59 (which added a NULLABLE tenant_id and
-- kept the global uq_library_name_version so nothing broke). This migration:
--   1. Backfills any rows still missing a tenant to the default tenant. V59 backfilled every
--      row that existed then, but the CDS-service sync insert path (CdsHooksService) created
--      libraries WITHOUT a tenant between V59 and this PR — those are repaired here. That
--      insert path is tenant-assigned as of this same PR, so no new tenant-less rows appear.
--   2. Makes tenant_id NOT NULL.
--   3. Replaces the global UNIQUE (name, version) with a tenant-scoped UNIQUE
--      (tenant_id, name, version) so different clinics may hold same-named libraries.
--
-- Safety of the constraint swap: the old uq_library_name_version (created in V4) made
-- (name, version) globally unique, so after backfilling every row to a tenant,
-- (tenant_id, name, version) is trivially unique too — the new constraint cannot fail on
-- existing data. The old constraint has a fixed name (V4), so a plain DROP CONSTRAINT
-- IF EXISTS suffices — no dynamic lookup needed (unlike V61's inline-named constraint).

-- 1. Ensure the default tenant exists (idempotent; also created in V57/V58/V59).
INSERT INTO tenant (code, name, active)
    SELECT 'default', 'Default Tenant', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE code = 'default');

-- 2. Repair any tenant-less libraries (see header note on CdsHooksService).
UPDATE cql_library
    SET tenant_id = (SELECT id FROM tenant WHERE code = 'default')
    WHERE tenant_id IS NULL;

-- 3. Enforce presence.
ALTER TABLE cql_library ALTER COLUMN tenant_id SET NOT NULL;

-- 4. Swap the uniqueness scope from global to per-tenant.
ALTER TABLE cql_library DROP CONSTRAINT IF EXISTS uq_library_name_version;
ALTER TABLE cql_library ADD CONSTRAINT uq_cql_library_tenant_name_version
    UNIQUE (tenant_id, name, version);
