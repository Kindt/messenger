-- Spec 022 US4: voice message duration metadata.

ALTER TABLE messages ADD COLUMN IF NOT EXISTS voice_duration_ms INT;

COMMENT ON COLUMN messages.voice_duration_ms IS 'Duration for type=voice messages (milliseconds).';
