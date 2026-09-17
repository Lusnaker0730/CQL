-- V65: Tenant foundation for failed_import (Phase 2 — #698, import-domain enforcement)
--
-- failed_import was NOT covered by V64 (an oversight caught during the import-domain
-- enforcement work): it stores patient_fhir_id UNENCRYPTED, so its cross-tenant exposure
-- is actually larger than patient_import's. Same foundation pattern as V64: nullable
-- tenant_id + backfill to the default tenant + FK + index. The enforcement changes in
-- this same PR assign the tenant on write and scope the management reads; hardening
-- (NOT NULL) lands with the rest of the #698 batch.

INSERT INTO tenant (code, name, active)
    SELECT 'default', 'Default Tenant', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE code = 'default');

ALTER TABLE failed_import ADD COLUMN tenant_id BIGINT;
UPDATE failed_import SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE failed_import ADD CONSTRAINT fk_failed_import_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_failed_import_tenant ON failed_import(tenant_id);
