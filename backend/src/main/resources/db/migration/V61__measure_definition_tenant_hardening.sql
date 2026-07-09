-- V61: Harden measure_definition tenant isolation (Phase 2)
--
-- All measure_definition reads are tenant-scoped (PAT-186 — no unscoped findByNameAndVersion
-- remains), so it is safe to make tenant_id NOT NULL and scope the unique constraint to the
-- tenant. This enables two tenants to hold a measure with the same name+version.

-- Defensive backfill: any row created before tenant assignment → default tenant.
UPDATE measure_definition SET tenant_id = (SELECT id FROM tenant WHERE code = 'default') WHERE tenant_id IS NULL;

ALTER TABLE measure_definition ALTER COLUMN tenant_id SET NOT NULL;

-- Drop the original UNIQUE (name, version) — created inline (auto-named) in V5. Find it by its
-- exact column set so we are robust to the auto-generated constraint name.
DO $$
DECLARE
    cname text;
BEGIN
    SELECT c.conname INTO cname
    FROM pg_constraint c
    WHERE c.conrelid = 'measure_definition'::regclass
      AND c.contype = 'u'
      AND (SELECT array_agg(a.attname::text ORDER BY a.attname::text)
           FROM unnest(c.conkey) AS k
           JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k)
          = ARRAY['name', 'version'];
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE measure_definition DROP CONSTRAINT %I', cname);
    END IF;
END $$;

ALTER TABLE measure_definition
    ADD CONSTRAINT uq_measure_definition_tenant_name_version UNIQUE (tenant_id, name, version);
