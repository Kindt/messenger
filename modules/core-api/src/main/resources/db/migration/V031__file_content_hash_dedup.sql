-- FR-OPT-08: content-addressed MinIO keys with shared blob refcount.
CREATE TABLE IF NOT EXISTS file_blob (
    content_hash VARCHAR(64) PRIMARY KEY,
    storage_key  VARCHAR(512) NOT NULL,
    blob_size    BIGINT NOT NULL DEFAULT 0,
    ref_count    INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE file_metadata
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS storage_key VARCHAR(512);

CREATE INDEX IF NOT EXISTS idx_file_metadata_content_hash ON file_metadata (content_hash);
