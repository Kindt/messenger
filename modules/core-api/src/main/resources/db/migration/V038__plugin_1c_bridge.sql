INSERT INTO plugin_presets (id, plugin_class, runtime_kind, capabilities)
VALUES
    ('1c-bridge', 'L2', 'bridge', '["catalog_read","document_status"]'::jsonb)
ON CONFLICT (id) DO NOTHING;
