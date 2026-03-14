-- Add locking fields for exclusive edit support
ALTER TABLE measure_definition ADD COLUMN locked_by VARCHAR(100);
ALTER TABLE measure_definition ADD COLUMN locked_at TIMESTAMP;

-- Add care setting classification
ALTER TABLE measure_definition ADD COLUMN setting VARCHAR(50);
