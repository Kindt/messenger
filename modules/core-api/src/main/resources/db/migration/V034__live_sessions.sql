-- Live-streaming sessions (spec 013 L2 POC): WebRTC via SFU (LiveKit), distinct from mesh calls / conferences

CREATE TABLE live_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id         UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    created_by      UUID NOT NULL REFERENCES users(id),
    title           VARCHAR(256) NOT NULL DEFAULT '',
    status          VARCHAR(16) NOT NULL DEFAULT 'active',
    mode            VARCHAR(16) NOT NULL DEFAULT 'webrtc',
    room_name       VARCHAR(160) NOT NULL UNIQUE,
    max_viewers     INT NOT NULL DEFAULT 200,
    viewer_count    INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ
);

CREATE INDEX idx_live_sessions_chat ON live_sessions(chat_id);
CREATE INDEX idx_live_sessions_status ON live_sessions(status);

CREATE TABLE live_session_viewers (
    session_id      UUID NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL DEFAULT 'viewer',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at         TIMESTAMPTZ,
    PRIMARY KEY (session_id, user_id)
);
