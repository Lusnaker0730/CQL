-- V2: Audit logging table for PHI access tracking (HIPAA compliance)

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL,
    method          VARCHAR(10)     NOT NULL,
    path            VARCHAR(500)    NOT NULL,
    resource_type   VARCHAR(50),
    resource_id     VARCHAR(100),
    action          VARCHAR(20)     NOT NULL,
    status_code     INTEGER,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    response_time_ms BIGINT,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_username ON audit_log(username);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_resource ON audit_log(resource_type, resource_id);
