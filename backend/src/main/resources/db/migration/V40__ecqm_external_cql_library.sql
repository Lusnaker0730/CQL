-- External CQL libraries for eCQM artifacts (mirrors cds_external_cql_library)
CREATE TABLE ecqm_external_cql_library (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    artifact_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50),
    fhir_version VARCHAR(20),
    cql_content TEXT NOT NULL,
    elm_json TEXT,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ecqm_ext_cql_artifact FOREIGN KEY (artifact_id)
        REFERENCES ecqm_artifact(id) ON DELETE CASCADE
);

CREATE INDEX idx_ecqm_ext_cql_artifact ON ecqm_external_cql_library(artifact_id);
