-- Spec 068 W1: avatar history table (write path deferred to W9).

CREATE TABLE avatar_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(16) NOT NULL,
    entity_id       UUID NOT NULL,
    file_id         UUID NOT NULL REFERENCES file_metadata(id) ON DELETE CASCADE,
    set_by_user_id  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT avatar_history_entity_type_check CHECK (entity_type IN ('user', 'chat'))
);

CREATE INDEX idx_avatar_history_entity ON avatar_history (entity_type, entity_id, created_at DESC);
