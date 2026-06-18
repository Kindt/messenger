-- Spec 022 US2: thread replies off main timeline (Slack-style).
ALTER TABLE messages
    ADD COLUMN thread_id UUID REFERENCES messages (id);

COMMENT ON COLUMN messages.thread_id IS
    'Root message id for thread replies; NULL = main timeline message';

CREATE INDEX idx_messages_chat_main_timeline
    ON messages (chat_id, created_at DESC)
    WHERE thread_id IS NULL AND deleted = false;

CREATE INDEX idx_messages_thread_timeline
    ON messages (chat_id, thread_id, created_at DESC)
    WHERE thread_id IS NOT NULL AND deleted = false;
