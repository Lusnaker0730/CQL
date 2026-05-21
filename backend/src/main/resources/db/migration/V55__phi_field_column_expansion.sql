-- PHI field encryption column-size expansion (PAT-100, pre-launch review #4 Phase 1)
-- AES-GCM + Base64 + "ENC:" prefix inflates ciphertext to roughly 1.5× the plaintext
-- length (IV 12B + tag 16B + base64 expansion). Columns previously sized for plaintext
-- PHI values must grow or inserts will fail with data-too-long errors.
--
-- result_json / subject_ids_json / bundle_json are already TEXT — no change needed.
-- patient_fhir_id / patient_identifier / patient_name had bounded VARCHAR lengths
-- that are too tight for encrypted values.
--
-- Change strategy: widen to TEXT rather than picking new VARCHAR lengths. Ciphertext
-- size is payload-dependent and TEXT has no practical upper bound in PostgreSQL;
-- any future payload growth won't require another migration.
--
-- Encrypt-on-read fallback in EncryptionConverter means existing rows (still plaintext)
-- continue to be readable; they get upgraded to ciphertext the next time they're
-- written. No bulk re-encryption is required by this migration.

ALTER TABLE patient_import
    ALTER COLUMN patient_fhir_id    TYPE TEXT,
    ALTER COLUMN patient_identifier TYPE TEXT,
    ALTER COLUMN patient_name       TYPE TEXT;

ALTER TABLE sandbox_preset
    ALTER COLUMN patient_id TYPE TEXT;
