CREATE TABLE mls_group_state (
    group_id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    epoch BIGINT NOT NULL DEFAULT 0,
    tree_data BYTEA NOT NULL DEFAULT '\\x',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_mls_group_chat ON mls_group_state(chat_id);
