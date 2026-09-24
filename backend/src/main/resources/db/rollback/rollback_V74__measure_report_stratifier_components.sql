-- Rollback V74: drop the per-component values of multi-component strata (PAT-235).
--
-- strata_value still holds the combined value, so nothing else is lost; roll the application
-- back to a pre-PAT-235 image at the same time (ddl-auto: validate expects the column).

ALTER TABLE measure_report_stratifier DROP COLUMN IF EXISTS component_values;
