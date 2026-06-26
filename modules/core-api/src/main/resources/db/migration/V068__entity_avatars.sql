-- Spec 068 W1: user avatars + chats.avatar_file_id UUID FK to file_metadata.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS avatar_file_id UUID REFERENCES file_metadata(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_users_avatar_file_id ON users (avatar_file_id)
    WHERE avatar_file_id IS NOT NULL;

-- Migrate chats.avatar_file_id from VARCHAR(128) to UUID with FK.
ALTER TABLE chats
    ALTER COLUMN avatar_file_id TYPE UUID
    USING CASE
        WHEN avatar_file_id IS NULL OR trim(avatar_file_id::text) = '' THEN NULL
        ELSE avatar_file_id::uuid
    END;

ALTER TABLE chats
    ADD CONSTRAINT fk_chats_avatar_file
    FOREIGN KEY (avatar_file_id) REFERENCES file_metadata(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_chats_avatar_file_id ON chats (avatar_file_id)
    WHERE avatar_file_id IS NOT NULL;
