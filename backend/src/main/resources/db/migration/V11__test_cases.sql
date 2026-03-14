CREATE TABLE test_case (
    id                        BIGSERIAL PRIMARY KEY,
    measure_definition_id     BIGINT       NOT NULL REFERENCES measure_definition(id) ON DELETE CASCADE,
    title                     VARCHAR(200) NOT NULL,
    description               VARCHAR(2000),
    patient_bundle_json       TEXT,
    expected_populations      TEXT,
    status                    VARCHAR(20)  DEFAULT 'pending',
    last_run_result_json      TEXT,
    last_run_actual_populations TEXT,
    last_run_at               TIMESTAMP,
    created_at                TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_test_case_measure_id ON test_case(measure_definition_id);
