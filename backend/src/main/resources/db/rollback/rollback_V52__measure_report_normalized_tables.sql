-- Rollback for V52__measure_report_normalized_tables.sql
-- Drop in reverse dependency order (children → parents)
DROP TABLE IF EXISTS measure_report_stratifier_population;
DROP TABLE IF EXISTS measure_report_stratifier;
DROP TABLE IF EXISTS measure_report_population;
DROP TABLE IF EXISTS measure_report_group;
