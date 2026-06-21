-- Spec 014: M365/Exchange integration pack (G-SUPER-01–03) + exchange L2 meeting scaffold

INSERT INTO plugin_presets (id, plugin_class, runtime_kind, capabilities)
VALUES
    ('owa-outlook', 'L0', 'config', '["deep_link","mail_read"]'::jsonb),
    ('sharepoint-onedrive', 'L0', 'config', '["deep_link","file_search"]'::jsonb)
ON CONFLICT (id) DO NOTHING;

UPDATE plugin_presets
SET capabilities = '["calendar_read","freebusy","meeting_create"]'::jsonb
WHERE id = 'exchange-bridge';

UPDATE plugin_presets
SET capabilities = '["file_search","file_link","sharepoint_graph"]'::jsonb
WHERE id = 'storage-bridge';
