-- Rollback V20: NQF/CMS Identifiers

ALTER TABLE measure_definition DROP COLUMN IF EXISTS nqf_number;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS cms_measure_id;
