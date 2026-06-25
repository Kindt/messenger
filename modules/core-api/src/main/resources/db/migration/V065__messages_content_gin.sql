-- Spec 025 FR-005 / FR-109: GIN full-text index for plaintext message search.
CREATE INDEX IF NOT EXISTS idx_messages_content_gin
    ON messages USING gin (to_tsvector('russian', coalesce(content, '')))
    WHERE deleted = false AND type NOT LIKE 'e2ee-%';
