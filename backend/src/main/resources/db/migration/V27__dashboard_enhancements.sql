-- V27: Dashboard Enhancements
-- Measure threshold table for alerts and quality tracking

CREATE TABLE measure_threshold (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    measure_definition_id BIGINT NOT NULL,
    threshold_type VARCHAR(30) NOT NULL,
    threshold_value DOUBLE NOT NULL,
    comparison_operator VARCHAR(10) NOT NULL DEFAULT '>=',
    department VARCHAR(100),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_threshold_measure FOREIGN KEY (measure_definition_id) REFERENCES measure_definition(id) ON DELETE CASCADE
);

CREATE INDEX idx_threshold_measure ON measure_threshold(measure_definition_id);
CREATE INDEX idx_threshold_department ON measure_threshold(department);
