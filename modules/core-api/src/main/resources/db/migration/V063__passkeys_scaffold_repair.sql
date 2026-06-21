-- Repair / ensure passkeys scaffold (G-SUPER lab; idempotent)

CREATE TABLE IF NOT EXISTS user_passkey_credentials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    credential_id VARCHAR(256) NOT NULL,
    public_key TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_passkey_cred ON user_passkey_credentials (credential_id);
