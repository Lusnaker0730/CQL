DROP INDEX IF EXISTS idx_audit_log_patient_fhir_id;
DROP INDEX IF EXISTS idx_audit_log_connection_id;
DROP INDEX IF EXISTS idx_audit_log_created_at;
ALTER TABLE audit_log DROP COLUMN IF EXISTS connection_name;
ALTER TABLE audit_log DROP COLUMN IF EXISTS patient_fhir_id;
ALTER TABLE audit_log DROP COLUMN IF EXISTS connection_id;
