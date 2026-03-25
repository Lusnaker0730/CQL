-- V45: Batch import job tracking table
CREATE TABLE batch_import_job (
    id BIGSERIAL PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    total_patients INT NOT NULL DEFAULT 0,
    completed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    patient_ids TEXT NOT NULL,
    measure_id BIGINT,
    result_summary TEXT,
    error_message VARCHAR(1000),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_batch_import_job_status ON batch_import_job (status);
CREATE INDEX idx_batch_import_job_created_by ON batch_import_job (created_by);
CREATE INDEX idx_batch_import_job_connection_id ON batch_import_job (connection_id);
