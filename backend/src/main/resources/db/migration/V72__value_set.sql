-- V72: platform-owned value sets (PAT-230).
--
-- Until now a value set could only be LOOKED UP (bundled TW Core IG, VSAC, a public
-- terminology server). A hospital that defines its own code list — NHI order codes, a local
-- drug formulary, an in-house diagnosis grouping — had nowhere to put it, so measures
-- referenced value sets nothing could resolve. This table is that place.
--
-- One row = one VERSION of one value set. (tenant_id, url, version) is unique; a CQL
-- `valueset "X": 'url' version '1.2.0'` pins a row, an unversioned reference resolves to the
-- newest active one. Content is an explicit code list (extensional): `concepts` is a JSON
-- array of {system, version?, code, display?}. Rule-based (intensional) definitions are not
-- evaluated here — an imported ValueSet must carry its codes.
--
-- Tenant-scoped from day one (NOT NULL + FK). Isolation is repository-level like
-- cql_library; the V70 row-level-security policies cover PHI tables only, and a value set
-- holds terminology, not patient data.

CREATE TABLE value_set (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    url             VARCHAR(500) NOT NULL,
    version         VARCHAR(50)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    title           VARCHAR(500),
    description     TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'draft',
    publisher       VARCHAR(255),
    concepts        TEXT         NOT NULL,
    concept_count   INTEGER      NOT NULL DEFAULT 0,
    origin          VARCHAR(20)  NOT NULL DEFAULT 'authored',
    source_json     TEXT,
    owner_username  VARCHAR(100) NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_value_set_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT uq_value_set_tenant_url_version UNIQUE (tenant_id, url, version)
);

CREATE INDEX idx_value_set_tenant ON value_set(tenant_id);
CREATE INDEX idx_value_set_tenant_url ON value_set(tenant_id, url);
