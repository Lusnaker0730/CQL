-- V68: Tenant isolation for cds_artifact and sandbox_preset (BUG-134)
--
-- These two tables were missed by every prior tenant batch: V57-V63 covered the measure/PHI
-- domain and V64/V66 the aux operational tables, but cds_artifact (V19) and sandbox_preset
-- (V33) were never touched. With no tenant_id, ArtifactService/SandboxPresetService looked
-- rows up by id alone, so OwnershipVerifier's ROLE_ADMIN bypass — which never consults
-- TenantContext — let a clinic ADMIN read and mutate any other tenant's artifacts.
--
-- Unlike V60->V61 and V64->V66 (foundation then hardening in separate PRs, with an inert
-- window where reads were not yet scoped), this ships the column and its NOT NULL together:
-- the read-scoping lands in the same change, so there is no window where code writes NULL.
--
-- cds_external_cql_library deliberately gets NO tenant_id: artifact_id is NOT NULL REFERENCES
-- cds_artifact ON DELETE CASCADE, so its tenant is definitionally its parent artifact's.
-- Denormalising it would create a second source of truth that can drift. Same reasoning as
-- test_case -> measure_definition (BUG-133).

-- Ensure a default tenant exists (idempotent; also created in V58/V59/V60).
INSERT INTO tenant (code, name, active)
    SELECT 'default', 'Default Tenant', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE code = 'default');

-- ===== cds_artifact =====

ALTER TABLE cds_artifact ADD COLUMN tenant_id BIGINT;

-- Map each row to its owner's tenant. Rows owned by legacy accounts (app_user.tenant_id IS
-- NULL) or by a username that no longer exists fall back to the default tenant — the same
-- resolution effectiveTenantId() applies at runtime.
UPDATE cds_artifact a
   SET tenant_id = COALESCE(
           (SELECT u.tenant_id FROM app_user u WHERE u.username = a.owner_username),
           (SELECT id FROM tenant WHERE code = 'default'))
 WHERE a.tenant_id IS NULL;

ALTER TABLE cds_artifact ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE cds_artifact ADD CONSTRAINT fk_cds_artifact_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_cds_artifact_tenant ON cds_artifact(tenant_id);

-- ===== sandbox_preset =====

ALTER TABLE sandbox_preset ADD COLUMN tenant_id BIGINT;

UPDATE sandbox_preset p
   SET tenant_id = COALESCE(
           (SELECT u.tenant_id FROM app_user u WHERE u.username = p.owner_username),
           (SELECT id FROM tenant WHERE code = 'default'))
 WHERE p.tenant_id IS NULL;

ALTER TABLE sandbox_preset ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE sandbox_preset ADD CONSTRAINT fk_sandbox_preset_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_sandbox_preset_tenant ON sandbox_preset(tenant_id);
