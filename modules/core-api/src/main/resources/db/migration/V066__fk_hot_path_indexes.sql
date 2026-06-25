-- Spec 025 FR-051 / FR-109: indexes on hot FK lookup paths.

CREATE INDEX IF NOT EXISTS idx_message_mentions_message_id
    ON message_mentions (message_id);

CREATE INDEX IF NOT EXISTS idx_messages_chat_sender
    ON messages (chat_id, sender_id);

CREATE INDEX IF NOT EXISTS idx_conference_participants_active
    ON conference_participants (conference_id)
    WHERE left_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_members_chat_user_active
    ON chat_members (chat_id, user_id)
    WHERE banned = false;
