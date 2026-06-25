-- Spec 025 FR-126: GIN trigram indexes for user search (username, display_name).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_users_username_trgm
    ON users USING gin (lower(username) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_display_name_trgm
    ON users USING gin (lower(display_name) gin_trgm_ops);
