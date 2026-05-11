CREATE TABLE file_metadata (
    id              UUID PRIMARY KEY,
    filename        VARCHAR(512) NOT NULL DEFAULT '',
    mime_type       VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream',
    size            BIGINT NOT NULL DEFAULT 0,
    uploaded_by     UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_file_metadata_uploaded_by ON file_metadata (uploaded_by);
