-- Spec 022: custom status text on user profile.

ALTER TABLE users ADD COLUMN IF NOT EXISTS custom_status_text VARCHAR(128) NOT NULL DEFAULT '';
