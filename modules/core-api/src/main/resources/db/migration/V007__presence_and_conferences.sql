-- Присутствие пользователя и групповые видеоконференции (сигналинг/комната; медиа — WebRTC у клиента или внешний Jitsi)

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS presence_status VARCHAR(16) NOT NULL DEFAULT 'offline',
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

CREATE TABLE conferences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id         UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    created_by      UUID NOT NULL REFERENCES users(id),
    title           VARCHAR(256) NOT NULL DEFAULT '',
    status          VARCHAR(16) NOT NULL DEFAULT 'active',
    room_slug       VARCHAR(160) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ
);

CREATE INDEX idx_conferences_chat ON conferences(chat_id);
CREATE INDEX idx_conferences_status ON conferences(status);

CREATE TABLE conference_participants (
    conference_id   UUID NOT NULL REFERENCES conferences(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at         TIMESTAMPTZ,
    PRIMARY KEY (conference_id, user_id)
);
