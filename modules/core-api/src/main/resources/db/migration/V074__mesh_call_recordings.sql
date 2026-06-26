-- Mesh WebRTC call sessions and recordings (audit always + optional user clip).

CREATE TABLE IF NOT EXISTS mesh_call_sessions (
    id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    started_by UUID NOT NULL REFERENCES users(id),
    media_mode VARCHAR(16) NOT NULL DEFAULT 'audio',
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    started_at TIMESTAMP NOT NULL DEFAULT now(),
    ended_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mesh_call_sessions_chat ON mesh_call_sessions (chat_id, started_at DESC);

CREATE TABLE IF NOT EXISTS mesh_call_recordings (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES mesh_call_sessions(id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    recorded_by UUID NOT NULL REFERENCES users(id),
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'recording',
    file_id UUID REFERENCES file_metadata(id),
    started_at TIMESTAMP NOT NULL DEFAULT now(),
    ended_at TIMESTAMP,
    duration_ms BIGINT
);

CREATE INDEX IF NOT EXISTS idx_mesh_call_recordings_session ON mesh_call_recordings (session_id, kind);
CREATE INDEX IF NOT EXISTS idx_mesh_call_recordings_user ON mesh_call_recordings (recorded_by, started_at DESC);
