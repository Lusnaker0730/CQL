-- Rollback V36: Audit PHI Access Columns

DROP INDEX IF EXISTS idx_audit_phi_access;

ALTER TABLE audit_log DROP COLUMN IF EXISTS phi_access;
ALTER TABLE audit_log DROP COLUMN IF EXISTS query_parameters;
