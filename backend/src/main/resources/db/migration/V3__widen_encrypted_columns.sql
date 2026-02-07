-- V3: Widen columns that store encrypted data (AES-GCM produces longer ciphertext)

ALTER TABLE app_user ALTER COLUMN email TYPE VARCHAR(500);
