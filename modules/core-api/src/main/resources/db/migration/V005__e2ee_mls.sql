CREATE TABLE e2ee_key_packages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    public_key      BYTEA NOT NULL,
    signature_key   BYTEA NOT NULL,
    cipher_suite    VARCHAR(32) NOT NULL DEFAULT 'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519',
    protocol_version VARCHAR(16) NOT NULL DEFAULT 'mls10',
    payload         JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ
);

CREATE INDEX idx_e2ee_key_packages_user_id ON e2ee_key_packages (user_id);

CREATE TABLE e2ee_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id         UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    epoch           BIGINT NOT NULL DEFAULT 0,
    cipher_suite    VARCHAR(32) NOT NULL DEFAULT 'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519',
    tree_hash       BYTEA,
    confirmed_transcript_hash BYTEA,
    group_context   JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_e2ee_sessions_chat_epoch ON e2ee_sessions (chat_id, epoch);

CREATE TABLE e2ee_message_keys (
    id              BIGSERIAL PRIMARY KEY,
    session_id      UUID NOT NULL REFERENCES e2ee_sessions(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    generation      BIGINT NOT NULL,
    key_data        BYTEA NOT NULL,
    used            BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_e2ee_message_keys_session ON e2ee_message_keys (session_id, sender_id);
