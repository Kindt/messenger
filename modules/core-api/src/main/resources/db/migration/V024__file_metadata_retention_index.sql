-- V024: index for file_metadata retention scans (phase C).
CREATE INDEX IF NOT EXISTS idx_file_metadata_created_at ON file_metadata(created_at);
