-- V58: Tenant isolation for ehr_connection (Phase 2 — first enforced resource)
--
-- Adds tenant_id to ehr_connection and backfills existing connections to a default
-- tenant so the current single-clinic deployment keeps working. From here, connection
-- MANAGEMENT (list / view / create) is tenant-scoped (PR#3). Execute-path scoping +
-- async tenant propagation is a follow-up (PR#4). Whole migration is one Flyway tx.

ALTER TABLE ehr_connection ADD COLUMN tenant_id BIGINT;

-- Ensure a default tenant exists.
INSERT INTO tenant (code, name, active)
    SELECT 'default', 'Default Tenant', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE code = 'default');

-- Backfill every existing connection to the default tenant.
UPDATE ehr_connection
    SET tenant_id = (SELECT id FROM tenant WHERE code = 'default')
    WHERE tenant_id IS NULL;

-- Every connection must belong to a tenant from now on.
ALTER TABLE ehr_connection ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE ehr_connection ADD CONSTRAINT fk_ehr_connection_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_ehr_connection_tenant ON ehr_connection(tenant_id);
