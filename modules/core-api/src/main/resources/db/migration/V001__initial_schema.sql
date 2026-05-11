CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(32) NOT NULL UNIQUE,
    display_name    VARCHAR(128) NOT NULL DEFAULT '',
    password_hash   VARCHAR(256),
    email           VARCHAR(256),
    phone           VARCHAR(20),
    phone_hash      VARCHAR(64),
    hidden          BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_phone_hash ON users (phone_hash);

CREATE TABLE devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name     VARCHAR(256) NOT NULL DEFAULT '',
    refresh_token_hash VARCHAR(256),
    push_token      VARCHAR(512),
    push_provider   VARCHAR(16),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_active_at  TIMESTAMPTZ
);

CREATE INDEX idx_devices_user_id ON devices (user_id);

CREATE TABLE contacts (
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, contact_user_id)
);

CREATE TABLE chats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(256) NOT NULL DEFAULT '',
    type            VARCHAR(16) NOT NULL DEFAULT 'p2p',
    owner_id        UUID REFERENCES users(id),
    avatar_file_id  VARCHAR(128),
    hidden          BOOLEAN NOT NULL DEFAULT false,
    muted           BOOLEAN NOT NULL DEFAULT false,
    ttl_seconds     INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chats_type_check CHECK (type IN ('p2p', 'group'))
);

CREATE TABLE chat_members (
    chat_id         UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL DEFAULT 'member',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    muted           BOOLEAN NOT NULL DEFAULT false,
    banned          BOOLEAN NOT NULL DEFAULT false,
    personal_filter_active BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (chat_id, user_id),
    CONSTRAINT chat_members_role_check CHECK (role IN ('owner', 'admin', 'member'))
);

CREATE INDEX idx_chat_members_user_id ON chat_members (user_id);
CREATE INDEX idx_chat_members_chat_id ON chat_members (chat_id);

CREATE TABLE blocks (
    blocker_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id)
);

CREATE INDEX idx_blocks_blocked_id ON blocks (blocked_id);

CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id         UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id),
    client_msg_id   VARCHAR(64),
    type            VARCHAR(16) NOT NULL DEFAULT 'text',
    content         TEXT,
    reply_to_msg_id UUID REFERENCES messages(id),
    deleted         BOOLEAN NOT NULL DEFAULT false,
    ttl_seconds     INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at       TIMESTAMPTZ
);

CREATE INDEX idx_messages_chat_id_created ON messages (chat_id, created_at DESC);
CREATE INDEX idx_messages_sender_id ON messages (sender_id);
CREATE UNIQUE INDEX idx_messages_dedup ON messages (sender_id, chat_id, client_msg_id)
    WHERE client_msg_id IS NOT NULL;

CREATE TABLE message_reactions (
    message_id      UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reaction        VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id, reaction)
);

CREATE TABLE message_versions (
    id              BIGSERIAL PRIMARY KEY,
    message_id      UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    content         TEXT,
    edited_by       UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_message_versions_message_id ON message_versions (message_id, created_at DESC);

CREATE TABLE pinned_messages (
    chat_id         UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    message_id      UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    pinned_by       UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chat_id, message_id)
);

-- audit_events: схема под админ-API в V008 (избегаем дублирования CREATE на чистой БД).
