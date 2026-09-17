-- Rollback V66: revert the auxiliary-table tenant hardening.
-- NOTE: re-adding the global UNIQUE(code) FAILS if two tenants already hold the same
-- department code (which V66 permitted). Resolve such duplicates before rolling back.
ALTER TABLE department DROP CONSTRAINT IF EXISTS uq_department_tenant_code;
ALTER TABLE department ADD CONSTRAINT department_code_key UNIQUE (code);

ALTER TABLE patient_import ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE batch_import_job ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE failed_import ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE audit_log ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE fhir_subscription ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE cds_service_config ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE user_api_keys ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE notification ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE department ALTER COLUMN tenant_id DROP NOT NULL;
