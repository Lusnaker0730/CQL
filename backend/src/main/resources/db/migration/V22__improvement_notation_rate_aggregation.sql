-- Add improvement notation and rate aggregation fields to measure_definition
ALTER TABLE measure_definition ADD COLUMN improvement_notation VARCHAR(20);
ALTER TABLE measure_definition ADD COLUMN rate_aggregation VARCHAR(2000);
