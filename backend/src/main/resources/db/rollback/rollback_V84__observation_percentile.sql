-- Rollback V84: drop the percentile of percentile-aggregated observations (PAT-264).
--
-- Reports keep obs_aggregate_method = 'percentile' and its value; only which percentile it was is lost.
-- Roll the application back to a pre-PAT-264 image at the same time (ddl-auto: validate expects the column).
ALTER TABLE measure_report_group DROP COLUMN IF EXISTS obs_percentile;
