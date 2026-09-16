-- PAT-223 — Row-Level Security, phase 1: the PHI-bearing tables.
--
-- Until now tenant isolation lived entirely in hand-written repository conditions
-- (findByIdAndTenantId ...). One forgotten condition = a cross-tenant leak, and the
-- 2026-06/07 access-control scan found 50+ of them (BUG-131 ~ BUG-139). These
-- policies are the second layer: even a query WITHOUT a tenant condition can only
-- see / write rows of the tenant the connection was scoped to.
--
-- How the scope reaches PostgreSQL: TenantAwareDataSource runs
--   SELECT set_config('app.tenant_id', <id>, false), set_config('app.rls_bypass', 'on'|'off', false)
-- on every connection checkout (see backend config/TenantAwareDataSource.java).
--   * app.tenant_id unset / ''  → app_current_tenant() IS NULL → no row matches (fail-closed)
--   * app.rls_bypass = 'on'     → system paths that legitimately span tenants
--                                 (startup backfill, migrations) — explicit, never the default
--
-- FORCE ROW LEVEL SECURITY makes the table OWNER subject to the policies too. The
-- application must connect as a NOSUPERUSER NOBYPASSRLS role (docker/postgres-init/
-- 10-app-role.sh, DB_APP_USERNAME / DB_APP_PASSWORD) — a superuser bypasses RLS no
-- matter what; TenantRlsStartupCheck reports that at boot. Future DATA migrations on
-- these tables run by a non-superuser owner must `SET LOCAL app.rls_bypass = 'on'` first.

CREATE OR REPLACE FUNCTION app_current_tenant() RETURNS BIGINT
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('app.tenant_id', true), '')::bigint $$;

CREATE OR REPLACE FUNCTION app_rls_bypass() RETURNS BOOLEAN
    LANGUAGE sql STABLE
AS $$ SELECT current_setting('app.rls_bypass', true) = 'on' $$;

-- ---------------------------------------------------------------------------
-- Tables with their own tenant_id column
-- ---------------------------------------------------------------------------

ALTER TABLE measure_report ENABLE ROW LEVEL SECURITY;
ALTER TABLE measure_report FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON measure_report
    USING      (app_rls_bypass() OR tenant_id = app_current_tenant())
    WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant());

ALTER TABLE patient_import ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_import FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON patient_import
    USING      (app_rls_bypass() OR tenant_id = app_current_tenant())
    WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant());

ALTER TABLE ehr_connection ENABLE ROW LEVEL SECURITY;
ALTER TABLE ehr_connection FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ehr_connection
    USING      (app_rls_bypass() OR tenant_id = app_current_tenant())
    WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant());

-- ---------------------------------------------------------------------------
-- Child tables that inherit their tenant from a parent (no tenant_id column)
-- ---------------------------------------------------------------------------

-- test_case holds real patient bundles; its tenant is the parent measure's (BUG-133).
ALTER TABLE test_case ENABLE ROW LEVEL SECURITY;
ALTER TABLE test_case FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON test_case
    USING (app_rls_bypass() OR EXISTS (
        SELECT 1 FROM measure_definition m
        WHERE m.id = test_case.measure_definition_id AND m.tenant_id = app_current_tenant()))
    WITH CHECK (app_rls_bypass() OR EXISTS (
        SELECT 1 FROM measure_definition m
        WHERE m.id = test_case.measure_definition_id AND m.tenant_id = app_current_tenant()));

-- Normalized report tables (V52): group → report; population / stratifier → group → report.
ALTER TABLE measure_report_group ENABLE ROW LEVEL SECURITY;
ALTER TABLE measure_report_group FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON measure_report_group
    USING (app_rls_bypass() OR EXISTS (
        SELECT 1 FROM measure_report r
        WHERE r.id = measure_report_group.measure_report_id AND r.tenant_id = app_current_tenant()))
    WITH CHECK (app_rls_bypass() OR EXISTS (
        SELECT 1 FROM measure_report r
        WHERE r.id = measure_report_group.measure_report_id AND r.tenant_id = app_current_tenant()));

ALTER TABLE measure_report_population ENABLE ROW LEVEL SECURITY;
ALTER TABLE measure_report_population FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON measure_report_population
    USING (app_rls_bypass() OR EXISTS (
        SELECT 1 FROM measure_report_group g JOIN measure_report r ON r.id = g.measure_report_id
        WHERE g.id = measure_report_population.measure_report_group_id AND r.tenant_id = app_current_tenant()))
    WITH CHECK (app_rls_bypass() OR EXISTS (
        SELECT 1 FROM measure_report_group g JOIN measure_report r ON r.id = g.measure_report_id
        WHERE g.id = measure_report_population.measure_report_group_id AND r.tenant_id = app_current_tenant()));

ALTER TABLE measure_report_stratifier ENABLE ROW LEVEL SECURITY;
ALTER TABLE measure_report_stratifier FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON measure_report_stratifier
    USING (app_rls_bypass() OR EXISTS (
        SELECT 1 FROM measure_report_group g JOIN measure_report r ON r.id = g.measure_report_id
        WHERE g.id = measure_report_stratifier.measure_report_group_id AND r.tenant_id = app_current_tenant()))
    WITH CHECK (app_rls_bypass() OR EXISTS (
        SELECT 1 FROM measure_report_group g JOIN measure_report r ON r.id = g.measure_report_id
        WHERE g.id = measure_report_stratifier.measure_report_group_id AND r.tenant_id = app_current_tenant()));
