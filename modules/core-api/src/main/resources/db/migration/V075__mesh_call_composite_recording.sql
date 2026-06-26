-- Unified server-side recording (LiveKit composite egress) for mesh call sessions.

ALTER TABLE mesh_call_sessions ADD COLUMN IF NOT EXISTS livekit_room VARCHAR(128);
ALTER TABLE mesh_call_sessions ADD COLUMN IF NOT EXISTS egress_id VARCHAR(128);
ALTER TABLE mesh_call_sessions ADD COLUMN IF NOT EXISTS recording_mode VARCHAR(16) NOT NULL DEFAULT 'mesh';

ALTER TABLE mesh_call_recordings ADD COLUMN IF NOT EXISTS egress_id VARCHAR(128);
ALTER TABLE mesh_call_recordings ADD COLUMN IF NOT EXISTS storage_key VARCHAR(512);
