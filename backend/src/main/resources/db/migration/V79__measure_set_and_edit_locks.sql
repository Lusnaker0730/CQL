-- PAT-253: measure set (version lineage) + edit locks on test cases and CQL libraries.
--
-- measure_set: one row per measure lineage. Until now versions of a measure were linked by
-- (tenant_id, name) — history, "supersede on approve" and version-number checks all keyed on the
-- name, so renaming one version silently split the lineage (and an unrelated measure created with
-- the same name silently joined it). Every measure_definition row now points at its set;
-- createVersionAs keeps the set id, create() opens a new set, a rename updates the set's name.
-- Backfill: one set per existing (tenant_id, name), which is exactly the lineage the name-based
-- code implied. Column is NOT NULL after the backfill (the application always sets it); the JPA
-- mapping stays nullable so H2 unit tests can build entities without a set.
-- No RLS: measure_definition itself is not under row-level security (only PHI-bearing tables
-- are, V70); tenant scoping is by query, as for measure_definition.
--
-- test_case / cql_library locked_by / locked_at: the same edit lock measure_definition has had
-- since V15 (holder-only writes, expiry via measure.locking.timeout-minutes). NULL = unlocked.

CREATE TABLE measure_set (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_measure_set_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);
CREATE INDEX idx_measure_set_tenant ON measure_set(tenant_id);

ALTER TABLE measure_definition ADD COLUMN measure_set_id BIGINT;

INSERT INTO measure_set (tenant_id, name, created_at, updated_at)
SELECT tenant_id, name, MIN(created_at), CURRENT_TIMESTAMP
FROM measure_definition
GROUP BY tenant_id, name;

UPDATE measure_definition m
SET measure_set_id = s.id
FROM measure_set s
WHERE s.tenant_id = m.tenant_id AND s.name = m.name;

ALTER TABLE measure_definition ALTER COLUMN measure_set_id SET NOT NULL;
ALTER TABLE measure_definition
    ADD CONSTRAINT fk_measure_definition_measure_set FOREIGN KEY (measure_set_id) REFERENCES measure_set(id);
CREATE INDEX idx_measure_definition_set ON measure_definition(measure_set_id);

ALTER TABLE test_case ADD COLUMN locked_by VARCHAR(100);
ALTER TABLE test_case ADD COLUMN locked_at TIMESTAMP;

ALTER TABLE cql_library ADD COLUMN locked_by VARCHAR(100);
ALTER TABLE cql_library ADD COLUMN locked_at TIMESTAMP;
