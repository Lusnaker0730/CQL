-- Add NQF Number and CMS Measure ID identifiers for eCQM compliance
ALTER TABLE measure_definition ADD COLUMN nqf_number VARCHAR(20);
ALTER TABLE measure_definition ADD COLUMN cms_measure_id VARCHAR(20);
