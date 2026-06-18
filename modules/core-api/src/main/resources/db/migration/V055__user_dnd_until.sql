-- Spec 022 US19: optional DND schedule end time.

ALTER TABLE users ADD COLUMN IF NOT EXISTS dnd_until TIMESTAMPTZ;
