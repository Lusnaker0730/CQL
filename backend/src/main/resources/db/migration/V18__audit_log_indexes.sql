-- Additional indexes on audit_log for dashboard query performance
CREATE INDEX IF NOT EXISTS idx_audit_status_code ON audit_log(status_code);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_log(action);
CREATE INDEX IF NOT EXISTS idx_audit_path ON audit_log(path);
