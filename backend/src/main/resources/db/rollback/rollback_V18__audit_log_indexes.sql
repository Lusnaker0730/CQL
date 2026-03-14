-- Rollback V18: Audit Log Indexes

DROP INDEX IF EXISTS idx_audit_status_code;
DROP INDEX IF EXISTS idx_audit_action;
DROP INDEX IF EXISTS idx_audit_path;
