package com.avandocmsg.messenger.api.admin.ui;

import com.avandocmsg.messenger.api.admin.ui.dto.AdminExportComplianceGuideResponse;

import java.util.List;

/** Operator checklist for export / retention env vars (admin UI reference). */
final class AdminExportEnvChecklist {

    private AdminExportEnvChecklist() {}

    static List<AdminExportComplianceGuideResponse.EnvChecklistItem> items() {
        return List.of(
            item("EXPORT_REPLAY_INCLUDE_FILE_BODIES", "ZIP with attachment bytes + manifest", "false"),
            item("EXPORT_REPLAY_INCLUDE_E2EE_FILE_CANDIDATES", "Heuristic file_metadata for chat members (E2EE chats)", "false"),
            item("EXPORT_REPLAY_MAX_E2EE_FILE_CANDIDATES", "Cap for e2eeFileCandidates block", "64"),
            item("EXPORT_ADMIN_SUGGEST_ENABLED", "POST export-compliance-prep, export-suggest", "false"),
            item("EXPORT_ADMIN_EXPORT_ENABLED", "POST /admin/chats/{id}/export (queue job)", "false"),
            item("EXPORT_REPLAY_INCLUDE_DEEP_ARCHIVE", "Merge deep-archive MinIO message snapshots", "false"),
            item("EXPORT_REPLAY_INCLUDE_RETENTION_SNAPSHOTS", "Merge retention hot-body MinIO snapshots", "false"),
            item("EXPORT_REPLAY_INCLUDE_SOLR_INDEX", "Attach Solr index dump for chat", "false"),
            item("EXPORT_REPLAY_APPLY_MESSAGE_TTL_FILTER", "Exclude messages past per-message TTL", "false"),
            item("RETENTION_PUBLISH_EXPORT_SUGGESTED", "Retention worker publishes msg.export.suggested", "false"),
            item("EXPORT_AUTO_QUEUE_ON_SUGGESTED", "Auto-queue export on suggestion (deduped)", "false"),
            item("EXPORT_AUTO_QUEUE_COOLDOWN_MINUTES", "Min minutes between auto-queues per chat", "1440"),
            item("EXPORT_SUGGESTED_SUBSCRIBER_ENABLED", "core-api audit on msg.export.suggested", "true"),
            item("EXPORT_REPLAY_METRICS_PORT", "export-replay worker /metrics + /health (0=off)", "0"),
            item("EXPORT_PROCESSING_STALE_MINUTES", "Stale processing threshold (DB gauge + admin stats)", "30"),
            item("EXPORT_REPLAY_CANCEL_CHECK_EVERY_ROWS", "Cooperative cancel DB poll interval during export", "500"),
            item("EXPORT_REPLAY_DEBUG_DELAY_MS", "Dev/smoke: pause after processing (ms)", "0")
        );
    }

    private static AdminExportComplianceGuideResponse.EnvChecklistItem item(
        String env, String purpose, String defaultValue) {
        return new AdminExportComplianceGuideResponse.EnvChecklistItem(env, purpose, defaultValue);
    }
}
