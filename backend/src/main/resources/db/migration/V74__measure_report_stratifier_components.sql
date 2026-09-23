-- PAT-235: multi-component strata (FHIR MeasureReport.group.stratifier.stratum.component).
-- The per-component values of a stratum, as a JSON array [{"code": "...", "value": "..."}];
-- NULL for a single-expression stratifier (every row written before this migration).
-- strata_value keeps the human-readable combination ("female | 65+"), so CSV / Excel / the
-- test-case runner keep working unchanged; the exchange export reads this column.

ALTER TABLE measure_report_stratifier ADD COLUMN component_values TEXT;
