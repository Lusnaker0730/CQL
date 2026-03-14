-- Measure Definition table - stores saved measure definitions
CREATE TABLE measure_definition (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    version VARCHAR(50) NOT NULL DEFAULT '1.0.0',
    title VARCHAR(500),
    description VARCHAR(2000),
    status VARCHAR(20) DEFAULT 'draft',
    scoring_type VARCHAR(30) DEFAULT 'proportion',
    cql_library_id VARCHAR(200),
    cql_content TEXT,
    fhir_measure_json TEXT,
    group_definitions TEXT,
    composite_scoring VARCHAR(30),
    component_measure_ids TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (name, version)
);

CREATE INDEX idx_measure_definition_name ON measure_definition (name);
CREATE INDEX idx_measure_definition_status ON measure_definition (status);
CREATE INDEX idx_measure_definition_scoring_type ON measure_definition (scoring_type);

-- Measure Report table - stores evaluation results
CREATE TABLE measure_report (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    measure_definition_id BIGINT,
    measure_name VARCHAR(200) NOT NULL,
    status VARCHAR(20),
    report_type VARCHAR(30),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    scoring_type VARCHAR(30),
    measure_score DOUBLE PRECISION,
    total_patients INTEGER,
    result_json TEXT NOT NULL,
    fhir_server_url VARCHAR(500),
    evaluated_by VARCHAR(100),
    evaluation_duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_measure_report_definition FOREIGN KEY (measure_definition_id) REFERENCES measure_definition(id) ON DELETE SET NULL
);

CREATE INDEX idx_measure_report_definition_id ON measure_report (measure_definition_id);
CREATE INDEX idx_measure_report_measure_name ON measure_report (measure_name);
CREATE INDEX idx_measure_report_period ON measure_report (period_start, period_end);
CREATE INDEX idx_measure_report_created_at ON measure_report (created_at);
