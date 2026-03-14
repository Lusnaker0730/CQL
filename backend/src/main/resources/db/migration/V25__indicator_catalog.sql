-- V25: Ministry of Health Indicator Code Mapping
-- Adds indicator code fields to measure_definition and creates indicator_catalog table

-- Add indicator code columns to measure_definition
ALTER TABLE measure_definition ADD COLUMN moh_indicator_code VARCHAR(50);
ALTER TABLE measure_definition ADD COLUMN nhia_p4p_code VARCHAR(50);
ALTER TABLE measure_definition ADD COLUMN drg_indicator_code VARCHAR(50);
ALTER TABLE measure_definition ADD COLUMN indicator_category VARCHAR(100);

-- Indicator catalog reference table
CREATE TABLE indicator_catalog (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(500) NOT NULL,
    name_en VARCHAR(500),
    category VARCHAR(100),
    subcategory VARCHAR(200),
    description VARCHAR(2000),
    source VARCHAR(50) NOT NULL,
    version VARCHAR(50),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_indicator_code_source UNIQUE (code, source)
);

CREATE INDEX idx_indicator_catalog_source ON indicator_catalog(source);
CREATE INDEX idx_indicator_catalog_category ON indicator_catalog(category);
CREATE INDEX idx_indicator_catalog_code ON indicator_catalog(code);
CREATE INDEX idx_measure_moh_code ON measure_definition(moh_indicator_code);
