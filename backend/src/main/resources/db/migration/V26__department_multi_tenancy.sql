-- V26: Department / Soft Multi-Tenancy
-- Tag-based department filtering for measures, reports, users, and artifacts

-- Department reference table
CREATE TABLE department (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    parent_code VARCHAR(100),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add department column to existing tables
ALTER TABLE app_user ADD COLUMN department VARCHAR(100);
ALTER TABLE measure_definition ADD COLUMN department VARCHAR(100);
ALTER TABLE measure_report ADD COLUMN department VARCHAR(100);
-- Note: cds_artifact department handled via authoring_artifact if it exists

-- Indexes for department filtering
CREATE INDEX idx_user_department ON app_user(department);
CREATE INDEX idx_measure_def_department ON measure_definition(department);
CREATE INDEX idx_measure_report_department ON measure_report(department);
-- cds_artifact index omitted (table may not exist)

-- Seed common hospital departments
INSERT INTO department (code, name, description, active, created_at, updated_at) VALUES
('INTERNAL', 'Internal Medicine', 'Department of Internal Medicine', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SURGERY', 'Surgery', 'Department of Surgery', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PEDIATRICS', 'Pediatrics', 'Department of Pediatrics', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('OBGYN', 'Obstetrics & Gynecology', 'Department of Obstetrics and Gynecology', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EMERGENCY', 'Emergency Medicine', 'Emergency Department', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('CARDIOLOGY', 'Cardiology', 'Department of Cardiology', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ONCOLOGY', 'Oncology', 'Department of Oncology', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NURSING', 'Nursing', 'Nursing Department', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PHARMACY', 'Pharmacy', 'Pharmacy Department', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('QM', 'Quality Management', 'Quality Management Office', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
