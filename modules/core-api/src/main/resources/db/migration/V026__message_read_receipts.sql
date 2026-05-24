CREATE TABLE message_read_receipts (
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    read_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);

CREATE INDEX idx_read_receipts_message ON message_read_receipts(message_id);
CREATE INDEX idx_read_receipts_user ON message_read_receipts(user_id);
CREATE INDEX idx_read_receipts_read_at ON message_read_receipts(read_at);
