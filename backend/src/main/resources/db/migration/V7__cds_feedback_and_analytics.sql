-- V5: CDS Hooks feedback, analytics, and service versioning

CREATE TABLE cds_feedback (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_id            VARCHAR(100) NOT NULL,
    card_uuid             VARCHAR(100),
    outcome               VARCHAR(20) NOT NULL,
    outcome_timestamp     TIMESTAMP,
    override_reason_code  VARCHAR(100),
    override_reason_display VARCHAR(500),
    accepted_suggestions  TEXT,
    hook_instance         VARCHAR(100),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_feedback_service FOREIGN KEY (service_id)
        REFERENCES cds_service_config(id) ON DELETE CASCADE
);
CREATE INDEX idx_feedback_service_id ON cds_feedback(service_id);
CREATE INDEX idx_feedback_created_at ON cds_feedback(created_at);

CREATE TABLE cds_service_analytics (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_id             VARCHAR(100) NOT NULL,
    invocation_count       BIGINT NOT NULL DEFAULT 0,
    error_count            BIGINT NOT NULL DEFAULT 0,
    total_response_time_ms BIGINT NOT NULL DEFAULT 0,
    last_invoked_at        TIMESTAMP,
    period_start           TIMESTAMP NOT NULL,
    period_end             TIMESTAMP,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_analytics_service FOREIGN KEY (service_id)
        REFERENCES cds_service_config(id) ON DELETE CASCADE
);
CREATE INDEX idx_analytics_service_id ON cds_service_analytics(service_id);

-- Versioning columns
ALTER TABLE cds_service_config ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE cds_service_config ADD COLUMN service_name VARCHAR(100);
UPDATE cds_service_config SET service_name = id WHERE service_name IS NULL;
ALTER TABLE cds_service_config ALTER COLUMN service_name SET NOT NULL;
CREATE INDEX idx_cds_service_name ON cds_service_config(service_name);
