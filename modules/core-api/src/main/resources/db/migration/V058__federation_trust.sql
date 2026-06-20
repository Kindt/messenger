-- Spec 022 T02308: cross-org federation trust registry (MVP)

CREATE TABLE IF NOT EXISTS federation_trust (
    id UUID PRIMARY KEY,
    org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    partner_org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_federation_trust_pair UNIQUE (org_id, partner_org_id),
    CONSTRAINT chk_federation_trust_status CHECK (status IN ('active', 'suspended', 'revoked'))
);

CREATE INDEX IF NOT EXISTS idx_federation_trust_org ON federation_trust (org_id, status);
CREATE INDEX IF NOT EXISTS idx_federation_trust_partner ON federation_trust (partner_org_id, status);
