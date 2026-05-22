CREATE INDEX IF NOT EXISTS idx_file_public_links_owner_active
    ON file_public_links (created_by, created_at DESC)
    WHERE revoked_at IS NULL;
