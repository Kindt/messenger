-- V025: extended legal hold flags (phase C).
ALTER TABLE org_retention_policy ADD COLUMN IF NOT EXISTS legal_hold_files BOOLEAN DEFAULT false;
ALTER TABLE org_retention_policy ADD COLUMN IF NOT EXISTS legal_hold_deep_archive BOOLEAN DEFAULT false;
ALTER TABLE chat_retention_policy ADD COLUMN IF NOT EXISTS legal_hold_files BOOLEAN DEFAULT false;
ALTER TABLE chat_retention_policy ADD COLUMN IF NOT EXISTS legal_hold_deep_archive BOOLEAN DEFAULT false;
