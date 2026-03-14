-- Rollback V22: Improvement Notation and Rate Aggregation

ALTER TABLE measure_definition DROP COLUMN IF EXISTS improvement_notation;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS rate_aggregation;
