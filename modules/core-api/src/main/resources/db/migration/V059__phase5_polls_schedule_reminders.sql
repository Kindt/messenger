-- Spec 022 Phase 5: polls, scheduled messages, message reminders (MVP scaffolds)

CREATE TABLE IF NOT EXISTS chat_polls (
    id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES users(id),
    question TEXT NOT NULL,
    options TEXT NOT NULL,
    allow_multiple BOOLEAN NOT NULL DEFAULT false,
    closes_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_chat_polls_chat ON chat_polls (chat_id, created_at DESC);

CREATE TABLE IF NOT EXISTS chat_poll_votes (
    poll_id UUID NOT NULL REFERENCES chat_polls(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    option_indexes TEXT NOT NULL,
    voted_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (poll_id, user_id)
);

CREATE TABLE IF NOT EXISTS scheduled_messages (
    id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id),
    message_type VARCHAR(32) NOT NULL DEFAULT 'text',
    content TEXT NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    reply_to_msg_id UUID,
    thread_id UUID,
    client_msg_id VARCHAR(128),
    sent_message_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_scheduled_messages_status CHECK (status IN ('pending', 'sent', 'failed', 'cancelled'))
);

CREATE INDEX IF NOT EXISTS idx_scheduled_messages_due ON scheduled_messages (status, scheduled_at);

CREATE TABLE IF NOT EXISTS message_reminders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    message_id UUID NOT NULL,
    remind_at TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_message_reminders_status CHECK (status IN ('pending', 'reminded', 'cancelled'))
);

CREATE INDEX IF NOT EXISTS idx_message_reminders_due ON message_reminders (status, remind_at);
CREATE INDEX IF NOT EXISTS idx_message_reminders_user ON message_reminders (user_id, status, remind_at);
