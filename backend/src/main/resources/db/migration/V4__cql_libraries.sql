CREATE TABLE cql_library (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    version         VARCHAR(50)  NOT NULL,
    cql_content     TEXT         NOT NULL,
    elm_json        TEXT,
    description     VARCHAR(2000),
    status          VARCHAR(20)  NOT NULL DEFAULT 'active',
    dependencies    TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_library_name_version UNIQUE (name, version)
);
CREATE INDEX idx_library_name ON cql_library(name);
CREATE INDEX idx_library_status ON cql_library(status);
