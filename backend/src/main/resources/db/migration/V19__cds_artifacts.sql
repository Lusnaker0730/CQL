-- CDS Authoring Tool - Artifact data model

CREATE TABLE IF NOT EXISTS cds_artifact (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) DEFAULT '1.0.0',
    description TEXT,
    status VARCHAR(20) DEFAULT 'draft',
    fhir_version VARCHAR(20) DEFAULT '4.0.1',

    -- CPG Metadata
    url VARCHAR(500),
    publisher VARCHAR(255),
    purpose TEXT,
    usage_info TEXT,
    copyright TEXT,
    experimental BOOLEAN DEFAULT FALSE,
    approval_date DATE,
    last_review_date DATE,
    effective_period_start DATE,
    effective_period_end DATE,
    strength_of_recommendation TEXT,
    quality_of_evidence TEXT,
    context TEXT,
    topic TEXT,
    author TEXT,
    reviewer TEXT,
    endorser TEXT,
    related_artifact TEXT,

    -- Expression Trees (core visual builder data)
    exp_tree_include TEXT NOT NULL DEFAULT '{"id":"And","name":"And","conjunction":true,"returnType":"boolean","childInstances":[]}',
    exp_tree_exclude TEXT NOT NULL DEFAULT '{"id":"And","name":"And","conjunction":true,"returnType":"boolean","childInstances":[]}',

    -- Linked data
    recommendations TEXT DEFAULT '[]',
    subpopulations TEXT DEFAULT '[]',
    base_elements TEXT DEFAULT '[]',
    parameters TEXT DEFAULT '[]',
    error_statement TEXT,

    -- Ownership
    owner_username VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cds_external_cql_library (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    artifact_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50),
    fhir_version VARCHAR(20),
    cql_content TEXT NOT NULL,
    elm_json TEXT,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ext_cql_artifact FOREIGN KEY (artifact_id) REFERENCES cds_artifact(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_cds_artifact_owner ON cds_artifact(owner_username);
CREATE INDEX IF NOT EXISTS idx_cds_artifact_status ON cds_artifact(status);
CREATE INDEX IF NOT EXISTS idx_ext_cql_artifact ON cds_external_cql_library(artifact_id);
