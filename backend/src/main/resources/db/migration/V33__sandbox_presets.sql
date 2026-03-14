CREATE TABLE sandbox_preset (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    owner_username VARCHAR(100) NOT NULL,
    service_id VARCHAR(100),
    patient_id VARCHAR(100),
    prefetch_json TEXT NOT NULL,
    shared BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sandbox_preset_owner ON sandbox_preset(owner_username);
