CREATE TABLE organizations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(256) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS org_id UUID REFERENCES organizations(id);

-- Идемпотентно на копиях БД, где могла остаться legacy-таблица из старого V001.
DROP TABLE IF EXISTS audit_events CASCADE;

CREATE TABLE audit_events (
    id              BIGSERIAL PRIMARY KEY,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_user_id   UUID REFERENCES users(id),
    action          VARCHAR(64) NOT NULL,
    resource_type   VARCHAR(64),
    resource_id     VARCHAR(128),
    details_json    TEXT
);

CREATE INDEX idx_audit_occurred ON audit_events (occurred_at DESC);

CREATE TABLE file_public_links (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id         UUID NOT NULL REFERENCES file_metadata(id) ON DELETE CASCADE,
    created_by      UUID NOT NULL REFERENCES users(id),
    link_kind       CHAR(1) NOT NULL CHECK (link_kind IN ('A', 'B', 'C')),
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    password_hash   VARCHAR(128),
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_file_public_links_file ON file_public_links (file_id);
CREATE INDEX idx_file_public_links_expires ON file_public_links (expires_at);

ALTER TABLE chats DROP CONSTRAINT IF EXISTS chats_type_check;
ALTER TABLE chats ADD CONSTRAINT chats_type_check CHECK (type IN ('p2p', 'group', 'saved'));
