-- V64: Tenant foundation for the auxiliary/operational tables (Phase 2 — #698)
--
-- Foundation only — INERT: reads are NOT yet tenant-scoped and tenant_id stays nullable.
-- Existing rows are backfilled to the default tenant; new rows keep getting created
-- without a tenant until each table's enforcement PR assigns it server-side and scopes
-- the reads (the V60 -> PAT-186/187 -> V61/V63 pattern used for the measure domain).
--
-- Tables, ordered by PHI sensitivity (see #698):
--   patient_import / batch_import_job  — patient-import records (PHI-linked)
--   audit_log                          — audit trail incl. patient_fhir_id + user activity
--   fhir_subscription / cds_service_config — attached to EHR connections / CDS config
--   user_api_keys / notification / department — user-owned auxiliary data

-- Ensure a default tenant exists (also created in V57-V60; idempotent).
INSERT INTO tenant (code, name, active)
    SELECT 'default', 'Default Tenant', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE code = 'default');

-- patient_import
ALTER TABLE patient_import ADD COLUMN tenant_id BIGINT;
UPDATE patient_import SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE patient_import ADD CONSTRAINT fk_patient_import_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_patient_import_tenant ON patient_import(tenant_id);

-- batch_import_job
ALTER TABLE batch_import_job ADD COLUMN tenant_id BIGINT;
UPDATE batch_import_job SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE batch_import_job ADD CONSTRAINT fk_batch_import_job_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_batch_import_job_tenant ON batch_import_job(tenant_id);

-- audit_log
ALTER TABLE audit_log ADD COLUMN tenant_id BIGINT;
UPDATE audit_log SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE audit_log ADD CONSTRAINT fk_audit_log_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_audit_log_tenant ON audit_log(tenant_id);

-- fhir_subscription
ALTER TABLE fhir_subscription ADD COLUMN tenant_id BIGINT;
UPDATE fhir_subscription SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE fhir_subscription ADD CONSTRAINT fk_fhir_subscription_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_fhir_subscription_tenant ON fhir_subscription(tenant_id);

-- cds_service_config
ALTER TABLE cds_service_config ADD COLUMN tenant_id BIGINT;
UPDATE cds_service_config SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE cds_service_config ADD CONSTRAINT fk_cds_service_config_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_cds_service_config_tenant ON cds_service_config(tenant_id);

-- user_api_keys
ALTER TABLE user_api_keys ADD COLUMN tenant_id BIGINT;
UPDATE user_api_keys SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE user_api_keys ADD CONSTRAINT fk_user_api_keys_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_user_api_keys_tenant ON user_api_keys(tenant_id);

-- notification
ALTER TABLE notification ADD COLUMN tenant_id BIGINT;
UPDATE notification SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE notification ADD CONSTRAINT fk_notification_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_notification_tenant ON notification(tenant_id);

-- department
ALTER TABLE department ADD COLUMN tenant_id BIGINT;
UPDATE department SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE department ADD CONSTRAINT fk_department_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_department_tenant ON department(tenant_id);
