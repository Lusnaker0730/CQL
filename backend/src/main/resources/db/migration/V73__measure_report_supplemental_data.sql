-- PAT-234: supplemental data elements and risk adjustment factors as value distributions
-- on a measure report. One row per (element, value) with the number of patients that had it;
-- a NULL value row carries the patients that had no value. Aggregates only — no patient ids.
--
-- Tenant scope is inherited from measure_report, like the other measure_report_* tables
-- (V70): RLS + FORCE with a policy that joins up to measure_report.tenant_id.

CREATE TABLE measure_report_supplemental_data (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    measure_report_id BIGINT NOT NULL,
    definition VARCHAR(500) NOT NULL,
    usage VARCHAR(50) NOT NULL,
    description TEXT,
    value VARCHAR(500),
    count INTEGER NOT NULL DEFAULT 0,
    ordinal INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_mrsd_report FOREIGN KEY (measure_report_id)
        REFERENCES measure_report(id) ON DELETE CASCADE,
    CONSTRAINT chk_mrsd_count_nonneg CHECK (count >= 0),
    CONSTRAINT chk_mrsd_usage CHECK (usage IN ('supplemental-data', 'risk-adjustment-factor'))
);

CREATE INDEX idx_mrsd_report ON measure_report_supplemental_data (measure_report_id);

ALTER TABLE measure_report_supplemental_data ENABLE ROW LEVEL SECURITY;
ALTER TABLE measure_report_supplemental_data FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON measure_report_supplemental_data
    USING (app_rls_bypass() OR EXISTS (
        SELECT 1 FROM measure_report r
        WHERE r.id = measure_report_supplemental_data.measure_report_id AND r.tenant_id = app_current_tenant()))
    WITH CHECK (app_rls_bypass() OR EXISTS (
        SELECT 1 FROM measure_report r
        WHERE r.id = measure_report_supplemental_data.measure_report_id AND r.tenant_id = app_current_tenant()));
