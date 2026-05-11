CREATE TABLE chat_bans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id         UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    banned_by       UUID NOT NULL REFERENCES users(id),
    reason          VARCHAR(512) NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_bans_chat_id ON chat_bans (chat_id);
CREATE INDEX idx_chat_bans_user_id ON chat_bans (user_id);
