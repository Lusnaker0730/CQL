-- Measure Schedule table - stores scheduled evaluation configurations
CREATE TABLE measure_schedule (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    measure_definition_id BIGINT NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    fhir_server_url VARCHAR(500),
    period_type VARCHAR(20) NOT NULL DEFAULT 'monthly',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at TIMESTAMP,
    last_run_status VARCHAR(20),
    next_run_at TIMESTAMP,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_measure_schedule_definition FOREIGN KEY (measure_definition_id) REFERENCES measure_definition(id) ON DELETE CASCADE
);

CREATE INDEX idx_measure_schedule_definition_id ON measure_schedule (measure_definition_id);
CREATE INDEX idx_measure_schedule_enabled ON measure_schedule (enabled);
CREATE INDEX idx_measure_schedule_next_run ON measure_schedule (next_run_at);
