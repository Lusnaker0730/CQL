-- Rollback V34: Fasting Lipid Rate Measure (Seed Data)
-- Deletes the seeded sample measure by name match.

DELETE FROM measure_definition WHERE name = 'FastingLipidRate';
