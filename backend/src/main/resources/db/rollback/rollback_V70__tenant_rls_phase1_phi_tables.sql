-- Rollback for V70__tenant_rls_phase1_phi_tables.sql (PAT-223).
-- Drops the tenant_isolation policies, turns RLS off again and removes the helper
-- functions. Run as the table owner. After this, isolation is back to the
-- hand-written repository conditions only.

DROP POLICY IF EXISTS tenant_isolation ON measure_report_stratifier;
ALTER TABLE measure_report_stratifier NO FORCE ROW LEVEL SECURITY;
ALTER TABLE measure_report_stratifier DISABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON measure_report_population;
ALTER TABLE measure_report_population NO FORCE ROW LEVEL SECURITY;
ALTER TABLE measure_report_population DISABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON measure_report_group;
ALTER TABLE measure_report_group NO FORCE ROW LEVEL SECURITY;
ALTER TABLE measure_report_group DISABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON test_case;
ALTER TABLE test_case NO FORCE ROW LEVEL SECURITY;
ALTER TABLE test_case DISABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON ehr_connection;
ALTER TABLE ehr_connection NO FORCE ROW LEVEL SECURITY;
ALTER TABLE ehr_connection DISABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON patient_import;
ALTER TABLE patient_import NO FORCE ROW LEVEL SECURITY;
ALTER TABLE patient_import DISABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON measure_report;
ALTER TABLE measure_report NO FORCE ROW LEVEL SECURITY;
ALTER TABLE measure_report DISABLE ROW LEVEL SECURITY;

DROP FUNCTION IF EXISTS app_rls_bypass();
DROP FUNCTION IF EXISTS app_current_tenant();

DELETE FROM flyway_schema_history WHERE version = '70';
