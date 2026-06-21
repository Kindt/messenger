-- Spec 022 Phase 5: STT mock + AI chat gateway presets (L2 bridge pattern)

INSERT INTO plugin_presets (id, plugin_class, runtime_kind, capabilities)
VALUES
    ('stt-mock', 'L2', 'bridge', '["stt_transcribe","conference_captions"]'::jsonb),
    ('ai-chat-gateway', 'L2', 'bridge', '["ai_assist","message_send"]'::jsonb)
ON CONFLICT (id) DO NOTHING;
