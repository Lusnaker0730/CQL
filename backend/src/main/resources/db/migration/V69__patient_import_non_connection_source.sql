-- V69: allow patient imports that did NOT come from a live EHR connection (PAT-206).
--
-- Until now every patient_import row came from an EHR connection ($everything fetch), so
-- connection_id was NOT NULL. 健康存摺 (My Health Bank) exports its records as FHIR bundles
-- that a clinic uploads as a file — there is no connection. Two changes:
--   1. connection_id becomes nullable (the FK stays; a NULL is simply not checked).
--   2. a `source` column records where the bundle came from ('ehr' | 'fhir-upload'),
--      so the two ingress paths are distinguishable and existing rows are labelled 'ehr'.
--
-- Tenant isolation is unchanged: patient_import.tenant_id is still NOT NULL (V66) and every
-- read is tenant-scoped.

ALTER TABLE patient_import ALTER COLUMN connection_id DROP NOT NULL;

ALTER TABLE patient_import ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'ehr';

-- Existing rows all came from an EHR connection; the DEFAULT already labels them 'ehr',
-- but set it explicitly so the intent is on the record and independent of the default.
UPDATE patient_import SET source = 'ehr' WHERE source IS NULL;
