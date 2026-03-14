-- Add PlanDefinition support to CDS service configuration
ALTER TABLE cds_service_config ADD COLUMN plan_definition_json TEXT;
ALTER TABLE cds_service_config ADD COLUMN card_generation_mode VARCHAR(20) DEFAULT 'cql_tuple';
