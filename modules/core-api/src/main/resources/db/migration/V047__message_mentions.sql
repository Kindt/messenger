-- Spec 022 US1: @user / @all mentions on messages.

CREATE TABLE message_mentions (
    message_id   UUID NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mention_kind VARCHAR(8) NOT NULL DEFAULT 'user',
    PRIMARY KEY (message_id, user_id),
    CONSTRAINT chk_message_mentions_kind CHECK (mention_kind IN ('user', 'all'))
);

CREATE INDEX idx_message_mentions_user ON message_mentions (user_id, message_id);
