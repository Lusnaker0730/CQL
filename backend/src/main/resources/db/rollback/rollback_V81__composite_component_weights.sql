-- Rollback V81: drop the composite component weights (PAT-259).
--
-- Weighted composites lose their weights (they would score as equal weights on the old image anyway);
-- composites saved with all-or-nothing / weighted scoring keep the value in composite_scoring, which the
-- pre-PAT-259 CompositeMeasureService treats as opportunity. Roll the application back with it.

ALTER TABLE measure_definition DROP COLUMN IF EXISTS component_weights;
