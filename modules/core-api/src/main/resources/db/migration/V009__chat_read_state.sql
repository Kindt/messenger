CREATE TABLE chat_read_state (
    user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chat_id               UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    last_read_message_id  UUID REFERENCES messages(id) ON DELETE SET NULL,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, chat_id)
);

CREATE INDEX idx_chat_read_state_chat ON chat_read_state (chat_id);
