-- Spec 014 P2/P3: bridge presets (exchange, storage, ocr, ai)

INSERT INTO plugin_presets (id, plugin_class, runtime_kind, capabilities)
VALUES
    ('exchange-bridge', 'L2', 'bridge', '["calendar_read","freebusy"]'::jsonb),
    ('storage-bridge', 'L2', 'bridge', '["file_search","file_link"]'::jsonb),
    ('ocr-invoice', 'L2', 'bridge', '["ocr","invoice_extract"]'::jsonb),
    ('ai-triage', 'L3', 'bridge', '["triage","rag","tool_calls"]'::jsonb)
ON CONFLICT (id) DO NOTHING;
