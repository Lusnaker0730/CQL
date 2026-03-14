-- Add reviewer/approver tracking and review comments to measure definitions
ALTER TABLE measure_definition ADD COLUMN reviewed_by VARCHAR(100);
ALTER TABLE measure_definition ADD COLUMN approved_by VARCHAR(100);
ALTER TABLE measure_definition ADD COLUMN review_comment VARCHAR(2000);
ALTER TABLE measure_definition ADD COLUMN reviewed_at TIMESTAMP;
