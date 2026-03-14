-- Test Case series grouping and sort order
ALTER TABLE test_case ADD COLUMN series VARCHAR(200);
ALTER TABLE test_case ADD COLUMN sort_order INT DEFAULT 0;
CREATE INDEX idx_test_case_series ON test_case (series);
