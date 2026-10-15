-- PAT-264: percentile aggregate for continuous-variable observations (MADiE gap item 3).
--   measure_report_group.obs_percentile  the percentile (0-100) that obs_aggregate_value is, when
--                                        obs_aggregate_method = 'percentile' (NULL for every other method).
-- Nullable, additive; the method itself already fits obs_aggregate_method VARCHAR(50).
ALTER TABLE measure_report_group ADD COLUMN obs_percentile DOUBLE PRECISION;
