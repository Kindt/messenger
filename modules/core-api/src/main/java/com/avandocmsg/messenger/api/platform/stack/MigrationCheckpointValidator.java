package com.avandocmsg.messenger.api.platform.stack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MigrationCheckpointValidator {

    private MigrationCheckpointValidator() {
    }

    public static ValidationResult validate(MigrationCheckpoint checkpoint) {
        var report = report(checkpoint);
        return new ValidationResult(false, report.failures(), java.util.List.of(), false, Map.of(
            "severity", report.severity(),
            "rollback_ready", Boolean.toString(report.rollbackReady()),
            "no_silent_fallback", Boolean.toString(report.noSilentFallback())
        ));
    }

    public static MigrationCheckpointReport report(MigrationCheckpoint checkpoint) {
        var failures = new ArrayList<String>();
        var missingMarkers = new ArrayList<String>();
        if (isBlank(checkpoint.sourceProfile())) {
            failures.add(checkpoint.component() + " checkpoint missing source_profile");
        }
        if (isBlank(checkpoint.targetProfile())) {
            failures.add(checkpoint.component() + " checkpoint missing target_profile");
        }
        if (isBlank(checkpoint.rollbackProfile())) {
            failures.add(checkpoint.component() + " checkpoint missing rollback_profile");
        }
        switch (checkpoint.component()) {
            case "relational-db-hot", "relational-db-archive" -> require(checkpoint, failures, missingMarkers,
                "backup_id", "flyway_version", "wal_lsn");
            case "object-storage" -> require(checkpoint, failures, missingMarkers,
                "inventory_time", "object_cursor", "checksum_manifest");
            case "messaging" -> require(checkpoint, failures, missingMarkers,
                "stream_sequence", "consumer_offset");
            case "search" -> require(checkpoint, failures, missingMarkers,
                "reindex_cursor", "index_schema_version", "shadow_target");
            default -> {
                if (isBlank(checkpoint.checkpointType())) {
                    failures.add(checkpoint.component() + " checkpoint missing checkpoint_type");
                }
            }
        }
        var rollbackReady = !isBlank(checkpoint.rollbackProfile()) && !isBlank(checkpoint.watchWindow());
        var passed = failures.isEmpty();
        return new MigrationCheckpointReport(
            checkpoint.component(),
            passed,
            passed ? "ok" : "blocker",
            missingMarkers,
            failures,
            rollbackReady,
            noSilentFallbackComponent(checkpoint.component())
        );
    }

    private static void require(
        MigrationCheckpoint checkpoint,
        ArrayList<String> failures,
        ArrayList<String> missingMarkers,
        String... keys
    ) {
        for (var key : keys) {
            if (isBlank(checkpoint.markers().get(key))) {
                failures.add(checkpoint.component() + " checkpoint missing " + key);
                missingMarkers.add(key);
            }
        }
    }

    private static boolean noSilentFallbackComponent(String component) {
        return List.of(
            "relational-db-hot",
            "relational-db-archive",
            "object-storage",
            "messaging",
            "idp",
            "search"
        ).contains(component);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
