-- Spec 014 P1: outbound delivery + additional presets

ALTER TABLE plugin_instances
    ADD COLUMN IF NOT EXISTS outbound_target_chat_id UUID,
    ADD COLUMN IF NOT EXISTS outbound_actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS outbound_token_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_plugin_instances_outbound
    ON plugin_instances (id) WHERE outbound_target_chat_id IS NOT NULL;

INSERT INTO plugin_presets (id, plugin_class, runtime_kind, capabilities)
VALUES
    ('jira-connector', 'L1', 'connector', '["jira_read","jira_create"]'::jsonb),
    ('confluence-connector', 'L1', 'connector', '["wiki_search"]'::jsonb),
    ('naumen-sd', 'L2', 'bridge', '["ticket_status","ticket_create"]'::jsonb),
    ('bitrix24-sidecar', 'L2', 'sidecar', '["crm_read","deal_notify"]'::jsonb),
    ('echo-sidecar', 'L1', 'sidecar', '["echo"]'::jsonb)
ON CONFLICT (id) DO NOTHING;
