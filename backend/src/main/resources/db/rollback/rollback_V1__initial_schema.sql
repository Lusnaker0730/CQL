-- Rollback V1: Initial Schema
-- WARNING: This drops ALL core tables. Data will be lost.
-- Ensure all dependent tables (V2+) are rolled back first.

DROP TABLE IF EXISTS cds_service_prefetch CASCADE;
DROP TABLE IF EXISTS cds_service_config CASCADE;
DROP TABLE IF EXISTS app_user CASCADE;
