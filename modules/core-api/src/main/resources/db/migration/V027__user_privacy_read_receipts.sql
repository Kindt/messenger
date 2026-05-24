ALTER TABLE users ADD COLUMN IF NOT EXISTS privacy_disable_read_receipts BOOLEAN NOT NULL DEFAULT false;
