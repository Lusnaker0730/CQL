CREATE TABLE fhir_subscription (
    id BIGSERIAL PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    fhir_subscription_id VARCHAR(200),
    criteria VARCHAR(500) NOT NULL,
    channel_type VARCHAR(20) NOT NULL DEFAULT 'rest-hook',
    callback_url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'requested',
    reason VARCHAR(500),
    error_message VARCHAR(1000),
    last_notification_at TIMESTAMP,
    notification_count INT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fhir_subscription_connection ON fhir_subscription (connection_id);
CREATE INDEX idx_fhir_subscription_status ON fhir_subscription (status);
