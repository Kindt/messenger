-- Spec 022 Phase 5 ADR backlog: parallel MVP scaffolds (repo-only, QEMU lab)

CREATE TABLE IF NOT EXISTS sticker_packs (
    id UUID PRIMARY KEY,
    org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sticker_packs_org ON sticker_packs (org_id, created_at DESC);

CREATE TABLE IF NOT EXISTS stickers (
    id UUID PRIMARY KEY,
    pack_id UUID NOT NULL REFERENCES sticker_packs(id) ON DELETE CASCADE,
    emoji_key VARCHAR(64),
    file_id UUID,
    animated BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_stickers_pack ON stickers (pack_id);

CREATE TABLE IF NOT EXISTS gif_catalog_entries (
    id UUID PRIMARY KEY,
    org_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
    query_key VARCHAR(128) NOT NULL,
    preview_url VARCHAR(1024),
    gif_url VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_gif_catalog_query ON gif_catalog_entries (org_id, query_key);

CREATE TABLE IF NOT EXISTS call_recordings (
    id UUID PRIMARY KEY,
    conference_id UUID NOT NULL,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    started_by UUID NOT NULL REFERENCES users(id),
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    storage_key VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_call_recordings_conf ON call_recordings (conference_id, created_at DESC);

CREATE TABLE IF NOT EXISTS conference_guest_links (
    id UUID PRIMARY KEY,
    conference_id UUID NOT NULL,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    waiting_room BOOLEAN NOT NULL DEFAULT true,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_conf_guest_conf ON conference_guest_links (conference_id);

CREATE TABLE IF NOT EXISTS conference_breakout_rooms (
    id UUID PRIMARY KEY,
    parent_conference_id UUID NOT NULL,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    livekit_room VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_breakout_parent ON conference_breakout_rooms (parent_conference_id);

CREATE TABLE IF NOT EXISTS chat_whiteboards (
    id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES users(id),
    title VARCHAR(256),
    snapshot_json TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_whiteboard_chat ON chat_whiteboards (chat_id);

CREATE TABLE IF NOT EXISTS chat_kanban_tasks (
    id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    column_key VARCHAR(32) NOT NULL DEFAULT 'todo',
    title VARCHAR(512) NOT NULL,
    assignee_id UUID REFERENCES users(id),
    created_by UUID NOT NULL REFERENCES users(id),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_kanban_chat ON chat_kanban_tasks (chat_id, column_key, sort_order);

CREATE TABLE IF NOT EXISTS org_sip_gateway (
    org_id UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT false,
    gateway_uri VARCHAR(512),
    h323_enabled BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_passkey_credentials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    credential_id VARCHAR(256) NOT NULL,
    public_key TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_passkey_cred ON user_passkey_credentials (credential_id);

CREATE TABLE IF NOT EXISTS live_caption_sessions (
    id UUID PRIMARY KEY,
    conference_id UUID NOT NULL,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    language VARCHAR(16) DEFAULT 'ru',
    transcript_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_captions_conf ON live_caption_sessions (conference_id, created_at DESC);
