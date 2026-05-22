ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS attachment_file_id UUID REFERENCES file_metadata(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_messages_attachment_file_id
    ON messages (attachment_file_id)
    WHERE attachment_file_id IS NOT NULL;
