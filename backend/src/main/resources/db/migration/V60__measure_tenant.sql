-- V60: Tenant foundation for the measure domain (measure_definition, ecqm_artifact, measure_report)
--
-- Foundation only — INERT: reads are NOT yet tenant-scoped. tenant_id is nullable and the
-- existing measure_definition UNIQUE(name, version) is retained. New rows get their tenant
-- assigned server-side (service create sites); existing rows are backfilled to the default
-- tenant here. The enforcement PR scopes reads and tightens NOT NULL / the unique constraint.

-- Ensure a default tenant exists (also created in V58/V59; idempotent).
INSERT INTO tenant (code, name, active)
    SELECT 'default', 'Default Tenant', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE code = 'default');

-- measure_definition
ALTER TABLE measure_definition ADD COLUMN tenant_id BIGINT;
UPDATE measure_definition SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE measure_definition ADD CONSTRAINT fk_measure_definition_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_measure_definition_tenant ON measure_definition(tenant_id);

-- ecqm_artifact
ALTER TABLE ecqm_artifact ADD COLUMN tenant_id BIGINT;
UPDATE ecqm_artifact SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE ecqm_artifact ADD CONSTRAINT fk_ecqm_artifact_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_ecqm_artifact_tenant ON ecqm_artifact(tenant_id);

-- measure_report
ALTER TABLE measure_report ADD COLUMN tenant_id BIGINT;
UPDATE measure_report SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE measure_report ADD CONSTRAINT fk_measure_report_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_measure_report_tenant ON measure_report(tenant_id);
