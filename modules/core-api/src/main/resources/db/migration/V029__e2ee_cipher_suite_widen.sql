-- Default MLS cipher suite names are 52 chars; VARCHAR(32) rejects INSERT/UPDATE.
ALTER TABLE e2ee_key_packages ALTER COLUMN cipher_suite TYPE VARCHAR(128);
ALTER TABLE e2ee_sessions ALTER COLUMN cipher_suite TYPE VARCHAR(128);
