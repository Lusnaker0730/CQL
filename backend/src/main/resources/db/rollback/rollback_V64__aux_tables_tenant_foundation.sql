-- Rollback V64: remove the auxiliary-table tenant foundation.
-- The backfill itself needs no reverting — the column is dropped wholesale.
DROP INDEX IF EXISTS idx_patient_import_tenant;
ALTER TABLE patient_import DROP CONSTRAINT IF EXISTS fk_patient_import_tenant;
ALTER TABLE patient_import DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS idx_batch_import_job_tenant;
ALTER TABLE batch_import_job DROP CONSTRAINT IF EXISTS fk_batch_import_job_tenant;
ALTER TABLE batch_import_job DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS idx_audit_log_tenant;
ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS fk_audit_log_tenant;
ALTER TABLE audit_log DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS idx_fhir_subscription_tenant;
ALTER TABLE fhir_subscription DROP CONSTRAINT IF EXISTS fk_fhir_subscription_tenant;
ALTER TABLE fhir_subscription DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS idx_cds_service_config_tenant;
ALTER TABLE cds_service_config DROP CONSTRAINT IF EXISTS fk_cds_service_config_tenant;
ALTER TABLE cds_service_config DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS idx_user_api_keys_tenant;
ALTER TABLE user_api_keys DROP CONSTRAINT IF EXISTS fk_user_api_keys_tenant;
ALTER TABLE user_api_keys DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS idx_notification_tenant;
ALTER TABLE notification DROP CONSTRAINT IF EXISTS fk_notification_tenant;
ALTER TABLE notification DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS idx_department_tenant;
ALTER TABLE department DROP CONSTRAINT IF EXISTS fk_department_tenant;
ALTER TABLE department DROP COLUMN IF EXISTS tenant_id;
