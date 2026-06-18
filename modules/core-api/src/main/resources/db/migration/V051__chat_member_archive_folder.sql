-- Spec 022 US12: per-user chat archive and folder tags.

ALTER TABLE chat_members ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;
ALTER TABLE chat_members ADD COLUMN IF NOT EXISTS folder_tag VARCHAR(32);

CREATE INDEX idx_chat_members_user_folder ON chat_members (user_id, folder_tag) WHERE folder_tag IS NOT NULL;
