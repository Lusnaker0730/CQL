-- Rollback V21: Plan Definition Support

ALTER TABLE cds_service_config DROP COLUMN IF EXISTS plan_definition_json;
ALTER TABLE cds_service_config DROP COLUMN IF EXISTS card_generation_mode;
