-- Rollback V42: Audit Log Request ID

DROP INDEX IF EXISTS idx_audit_request_id;
ALTER TABLE audit_log DROP COLUMN IF EXISTS request_id;
