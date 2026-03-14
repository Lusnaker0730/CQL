-- Rollback V30: Knee Replacement SSI Measure (Seed Data)
-- Deletes the seeded sample measure by name match.

DELETE FROM measure_definition WHERE name = 'KneeReplacementSSI';
