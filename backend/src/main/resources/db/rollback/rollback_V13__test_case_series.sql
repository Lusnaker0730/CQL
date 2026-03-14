-- Rollback V13: Test Case Series

DROP INDEX IF EXISTS idx_test_case_series;

ALTER TABLE test_case DROP COLUMN IF EXISTS series;
ALTER TABLE test_case DROP COLUMN IF EXISTS sort_order;
