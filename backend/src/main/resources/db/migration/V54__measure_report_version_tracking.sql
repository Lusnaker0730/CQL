-- Measure version + CQL/ELM hash tracking on measure_report (PAT-095, pre-launch review #3)
-- Makes historical reports reproducible and auditable: "which version of the measure,
-- exactly which CQL bytes, exactly which translated ELM produced this result" — the
-- three pieces a TFDA / CMS reviewer asks for at audit time.
--
-- All nullable because we can't retroactively populate the provenance of existing rows.
-- New reports get them filled at save time by MeasureReportService.

ALTER TABLE measure_report
    ADD COLUMN measure_version VARCHAR(50) NULL,
    ADD COLUMN cql_hash VARCHAR(64) NULL,
    ADD COLUMN elm_hash VARCHAR(64) NULL;

-- Covering index on (measure_definition_id, measure_version): the "all reports for
-- measure X at version v" query that audit workflows drive. Measure_version alone
-- isn't useful because versions collide across different measures.
CREATE INDEX idx_measure_report_definition_version
    ON measure_report (measure_definition_id, measure_version);
