-- eCQM Artifact table for visual eCQM CQL builder
CREATE TABLE ecqm_artifact (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) DEFAULT '1.0.0',
    description TEXT,
    status VARCHAR(20) DEFAULT 'draft',
    fhir_version VARCHAR(20) DEFAULT '4.0.1',
    scoring_type VARCHAR(30) NOT NULL DEFAULT 'proportion',
    population_basis VARCHAR(20) DEFAULT 'boolean',
    improvement_notation VARCHAR(20) DEFAULT 'increase',
    measure_set VARCHAR(200),
    cms_measure_id VARCHAR(20),
    nqf_number VARCHAR(20),
    url VARCHAR(500),
    publisher VARCHAR(255),
    purpose TEXT,
    copyright TEXT,
    rationale TEXT,
    clinical_guidance TEXT,
    steward VARCHAR(500),
    disclaimer TEXT,
    supplemental_data_guidance TEXT,
    population_groups TEXT NOT NULL DEFAULT '[]',
    supplemental_data TEXT DEFAULT '[]',
    stratifiers TEXT DEFAULT '[]',
    base_elements TEXT DEFAULT '[]',
    parameters TEXT DEFAULT '[]',
    published_measure_id BIGINT,
    owner_username VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ecqm_published_measure FOREIGN KEY (published_measure_id)
        REFERENCES measure_definition(id) ON DELETE SET NULL
);

CREATE INDEX idx_ecqm_artifact_owner ON ecqm_artifact(owner_username);
