-- Rollback V23: Review Workflow Fields

ALTER TABLE measure_definition DROP COLUMN IF EXISTS reviewed_by;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS approved_by;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS review_comment;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS reviewed_at;
