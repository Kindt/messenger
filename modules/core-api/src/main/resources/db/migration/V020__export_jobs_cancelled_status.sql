-- Allow export_cancelled terminal status (cooperative cancel API + worker).
ALTER TABLE export_jobs DROP CONSTRAINT IF EXISTS export_jobs_status_check;
ALTER TABLE export_jobs ADD CONSTRAINT export_jobs_status_check CHECK (
    status IN ('queued', 'processing', 'export_v1', 'stub_written', 'export_failed', 'export_cancelled')
);

CREATE INDEX IF NOT EXISTS idx_export_jobs_status_created ON export_jobs (status, created_at DESC);
