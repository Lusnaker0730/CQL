CREATE TABLE connection_health_check (
    id BIGSERIAL PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_time_ms BIGINT,
    error_message VARCHAR(1000),
    fhir_version VARCHAR(20),
    server_name VARCHAR(200),
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_health_check_connection ON connection_health_check (connection_id);
CREATE INDEX idx_health_check_checked_at ON connection_health_check (checked_at);
