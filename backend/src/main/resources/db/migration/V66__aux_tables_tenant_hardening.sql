-- V66: Enforce tenant presence on the auxiliary/operational tables (Phase 2 — #698 final)
--
-- Completes the #698 batch: V64/V65 added nullable tenant_id + backfill; the enforcement
-- PRs (PAT-195..199) made every write path assign a tenant and every management read
-- tenant-scoped. This migration makes the DB reject tenant-less rows outright, and swaps
-- department's global UNIQUE(code) for a tenant-scoped (tenant_id, code) so clinics can
-- each hold the seeded codes (INTERNAL/SURGERY/... from V26).
--
-- Safety: every table was fully backfilled in V64/V65 and all insert paths assign a
-- tenant as of PAT-195..199, so the defensive backfills below repair only rows written
-- in the deploy gap between those PRs and this migration.

-- 1. Ensure the default tenant exists (idempotent; also created in V57-V60/V64/V65).
INSERT INTO tenant (code, name, active)
    SELECT 'default', 'Default Tenant', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE code = 'default');

-- 2. Defensive backfill + NOT NULL, table by table.
UPDATE patient_import SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE patient_import ALTER COLUMN tenant_id SET NOT NULL;

UPDATE batch_import_job SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE batch_import_job ALTER COLUMN tenant_id SET NOT NULL;

UPDATE failed_import SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE failed_import ALTER COLUMN tenant_id SET NOT NULL;

UPDATE audit_log SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE audit_log ALTER COLUMN tenant_id SET NOT NULL;

UPDATE fhir_subscription SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE fhir_subscription ALTER COLUMN tenant_id SET NOT NULL;

UPDATE cds_service_config SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE cds_service_config ALTER COLUMN tenant_id SET NOT NULL;

UPDATE user_api_keys SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE user_api_keys ALTER COLUMN tenant_id SET NOT NULL;

UPDATE notification SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE notification ALTER COLUMN tenant_id SET NOT NULL;

UPDATE department SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;
ALTER TABLE department ALTER COLUMN tenant_id SET NOT NULL;

-- 3. department: swap the global UNIQUE(code) (V26, inline => postgres auto-named) for a
--    tenant-scoped unique. Auto-named constraint => dynamic lookup by column set, same
--    proven pattern as V61 (note the ::text cast — array_agg(a.attname) yields name[],
--    which cannot compare against text[]; caught on real postgres in PAT-190).
--    Constraint-swap safety: the old constraint guaranteed code globally unique, so after
--    the backfill (tenant_id, code) is trivially unique — the new constraint cannot fail.
DO $$
DECLARE
    conname_to_drop text;
BEGIN
    SELECT c.conname INTO conname_to_drop
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    WHERE t.relname = 'department'
      AND c.contype = 'u'
      AND (SELECT array_agg(a.attname::text ORDER BY a.attname)
           FROM unnest(c.conkey) AS k(attnum)
           JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum) = ARRAY['code']::text[];
    IF conname_to_drop IS NOT NULL THEN
        EXECUTE format('ALTER TABLE department DROP CONSTRAINT %I', conname_to_drop);
    END IF;
END $$;

ALTER TABLE department ADD CONSTRAINT uq_department_tenant_code UNIQUE (tenant_id, code);
