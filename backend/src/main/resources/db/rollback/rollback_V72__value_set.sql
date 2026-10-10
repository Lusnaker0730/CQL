-- Rollback V72: drop platform-owned value sets (PAT-230).
--
-- WARNING: this DELETES every value set authored or imported in the platform. Measures that
-- reference them keep their CQL, but the references stop resolving (no other terminology
-- source knows these URLs), so those measures can no longer be evaluated correctly. Export
-- the value sets first
-- (GET /api/value-sets/{id}/fhir) and roll the application back to a pre-PAT-230 image at the
-- same time — the post-PAT-230 code requires the table (ddl-auto: validate fails without it).

DROP INDEX IF EXISTS idx_value_set_tenant_url;
DROP INDEX IF EXISTS idx_value_set_tenant;
DROP TABLE IF EXISTS value_set;
