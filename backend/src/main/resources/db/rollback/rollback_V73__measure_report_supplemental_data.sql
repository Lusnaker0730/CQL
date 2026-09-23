-- Rollback V73: drop the supplemental data / risk adjustment factor distributions (PAT-234).
--
-- Only the distribution rows are lost; the report's result_json still carries them, so a
-- post-PAT-234 image re-reads them from there. Roll the application back to a pre-PAT-234
-- image at the same time — the post-PAT-234 code requires the table (ddl-auto: validate
-- fails without it).

DROP POLICY IF EXISTS tenant_isolation ON measure_report_supplemental_data;
DROP INDEX IF EXISTS idx_mrsd_report;
DROP TABLE IF EXISTS measure_report_supplemental_data;
