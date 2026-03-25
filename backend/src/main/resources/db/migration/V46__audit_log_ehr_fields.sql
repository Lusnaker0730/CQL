-- V46: Add EHR-specific audit fields and retention index
ALTER TABLE audit_log ADD COLUMN connection_id BIGINT;
ALTER TABLE audit_log ADD COLUMN patient_fhir_id VARCHAR(200);
ALTER TABLE audit_log ADD COLUMN connection_name VARCHAR(200);

-- Index for retention cleanup (delete by date)
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log (created_at);

-- Index for EHR audit queries
CREATE INDEX IF NOT EXISTS idx_audit_log_connection_id ON audit_log (connection_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_patient_fhir_id ON audit_log (patient_fhir_id);
