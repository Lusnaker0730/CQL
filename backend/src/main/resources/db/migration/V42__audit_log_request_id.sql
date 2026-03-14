-- V42: Add request_id column to audit_log for end-to-end request tracing.
-- Correlates with X-Request-ID header and MDC requestId in structured logs.

ALTER TABLE audit_log ADD COLUMN request_id VARCHAR(36);
CREATE INDEX idx_audit_request_id ON audit_log(request_id);
