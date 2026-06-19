-- Spec 022 US5: broadcast channels (admins post, members read).

ALTER TABLE chats DROP CONSTRAINT IF EXISTS chats_type_check;
ALTER TABLE chats ADD CONSTRAINT chats_type_check CHECK (type IN ('p2p', 'group', 'channel', 'saved'));

ALTER TABLE chats ADD COLUMN IF NOT EXISTS channel_post_policy VARCHAR(16) NOT NULL DEFAULT 'admins_only';

COMMENT ON COLUMN chats.channel_post_policy IS 'channel only: admins_only | all_members';
