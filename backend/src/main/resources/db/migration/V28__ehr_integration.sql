-- V28: EHR/HIS Integration Connector
-- Connection management and patient import tracking

CREATE TABLE ehr_connection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    fhir_server_url VARCHAR(500) NOT NULL,
    auth_type VARCHAR(20) NOT NULL DEFAULT 'none',
    credentials VARCHAR(2000),
    department VARCHAR(100),
    status VARCHAR(20) DEFAULT 'untested',
    last_tested_at TIMESTAMP,
    last_test_message VARCHAR(500),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE patient_import (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    patient_fhir_id VARCHAR(200) NOT NULL,
    patient_identifier VARCHAR(200),
    patient_name VARCHAR(500),
    resource_count INT DEFAULT 0,
    bundle_json CLOB,
    target_measure_id BIGINT,
    target_test_case_id BIGINT,
    imported_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_import_connection FOREIGN KEY (connection_id) REFERENCES ehr_connection(id) ON DELETE CASCADE,
    CONSTRAINT fk_import_measure FOREIGN KEY (target_measure_id) REFERENCES measure_definition(id) ON DELETE SET NULL,
    CONSTRAINT fk_import_test_case FOREIGN KEY (target_test_case_id) REFERENCES test_case(id) ON DELETE SET NULL
);

CREATE INDEX idx_ehr_connection_status ON ehr_connection(status);
CREATE INDEX idx_ehr_connection_department ON ehr_connection(department);
CREATE INDEX idx_patient_import_fhir_id ON patient_import(patient_fhir_id);
