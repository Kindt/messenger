-- Admin audit list: GET /admin/audit-events?action=... ORDER BY occurred_at DESC LIMIT
CREATE INDEX IF NOT EXISTS idx_audit_events_action_occurred ON audit_events (action, occurred_at DESC);

-- Same ordering with resource_id filter (UUID / message id, etc.)
CREATE INDEX IF NOT EXISTS idx_audit_events_resource_occurred ON audit_events (resource_id, occurred_at DESC);
