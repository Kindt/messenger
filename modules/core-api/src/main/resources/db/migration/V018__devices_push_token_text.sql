-- Web Push subscription JSON exceeds VARCHAR(512); store full subscription for provider "web".
ALTER TABLE devices ALTER COLUMN push_token TYPE TEXT;
