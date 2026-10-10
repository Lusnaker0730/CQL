-- Rollback for V71__test_case_expected_values.sql (PAT-228).
-- Drops the structured expected values. Test cases that only had structured expectations
-- fall back to their (possibly empty) legacy expected_populations map — export them first
-- if they matter.

ALTER TABLE test_case DROP COLUMN IF EXISTS expected_values;

DELETE FROM flyway_schema_history WHERE version = '71';
