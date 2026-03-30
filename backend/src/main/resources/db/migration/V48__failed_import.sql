-- V48: Failed import tracking for error recovery
CREATE TABLE failed_import (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    patient_fhir_id VARCHAR(200) NOT NULL,
    measure_id BIGINT,
    error_message VARCHAR(2000),
    error_type VARCHAR(100),
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_retry_at TIMESTAMP,
    resolved_at TIMESTAMP
);

CREATE INDEX idx_failed_import_status ON failed_import (status);
CREATE INDEX idx_failed_import_next_retry ON failed_import (next_retry_at, status);
CREATE INDEX idx_failed_import_connection ON failed_import (connection_id);
