-- V63: Enforce tenant presence on measure_report (Phase 2 — hardening)
--
-- Completes the measure_report tenant work: V60 added a NULLABLE tenant_id and backfilled
-- existing rows; the read paths were tenant-scoped by the PAT-187 enforcement PR and the
-- scheduled-evaluation path runs under the measure's tenant since PAT-189. The single insert
-- path (MeasureReportService.saveReport) assigns effectiveTenantId() since PAT-187, so no
-- new tenant-less rows are produced. This migration:
--   1. Backfills any rows still missing a tenant to the default tenant (defensive — covers
--      reports written between V60 and the PAT-187 deploy, and any transitional gap).
--   2. Makes tenant_id NOT NULL, so the DB rejects tenant-less PHI reports even if a future
--      insert path forgets the assignment.
--
-- Unlike measure_definition (V61) / cql_library (V62) there is no unique-constraint swap:
-- measure_report is an append-only report log with no (name, version)-style identity.

-- 1. Ensure the default tenant exists (idempotent; also created in V57/V58/V59/V60).
INSERT INTO tenant (code, name, active)
    SELECT 'default', 'Default Tenant', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE code = 'default');

-- 2. Repair any tenant-less reports (see header note).
UPDATE measure_report
    SET tenant_id = (SELECT id FROM tenant WHERE code = 'default')
    WHERE tenant_id IS NULL;

-- 3. Enforce presence.
ALTER TABLE measure_report ALTER COLUMN tenant_id SET NOT NULL;
