-- Rollback V7: CDS Feedback and Analytics

DROP TABLE IF EXISTS cds_service_analytics CASCADE;
DROP TABLE IF EXISTS cds_feedback CASCADE;

ALTER TABLE cds_service_config DROP COLUMN IF EXISTS version;
ALTER TABLE cds_service_config DROP COLUMN IF EXISTS service_name;
