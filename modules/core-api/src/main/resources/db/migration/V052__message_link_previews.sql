-- Spec 022 US13: link preview cache per message (QEMU unfurl worker).

CREATE TABLE message_link_previews (
    message_id UUID PRIMARY KEY REFERENCES messages (id) ON DELETE CASCADE,
    url        TEXT NOT NULL,
    title      TEXT,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
