-- Spec 022 T02201: DLP mock L2 bridge preset (ADR dlp-compliance-vs-bridge)

INSERT INTO plugin_presets (id, plugin_class, runtime_kind, capabilities)
VALUES ('dlp-mock', 'L2', 'bridge', '["dlp_scan","message_send"]'::jsonb)
ON CONFLICT (id) DO NOTHING;
