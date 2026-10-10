-- V71: structured expected values for measure test cases (PAT-228).
--
-- test_case.expected_populations is a flat {"numerator": true} map: one boolean per
-- population TYPE. That cannot express
--   * multi-group measures (two groups both have a "numerator" — the keys collide),
--   * measure observation values (continuous-variable / ratio),
--   * which stratum a patient is expected to fall into.
-- expected_values holds the structured form (JSON, per group: population counts,
-- observation values, stratifier values). NULL = the test case still uses the legacy
-- boolean map, so existing rows keep their exact behaviour.
--
-- Counts are integers rather than booleans on purpose: the evaluator is patient-based
-- today (0 / 1), but the stored format does not have to change when episode-level
-- population counting lands.
--
-- test_case is under row-level security since V70; adding a column does not touch the
-- policy (it keys off measure_definition_id).

ALTER TABLE test_case ADD COLUMN expected_values TEXT;
