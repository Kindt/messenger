package com.avandocmsg.messenger.api.platform.stack;

import java.util.ArrayList;
import java.util.Map;

public final class MigrationCheckpointValidator {

    private MigrationCheckpointValidator() {
    }

    public static ValidationResult validate(MigrationCheckpoint checkpoint) {
        var failures = new ArrayList<String>();
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
            case "relational-db-hot", "relational-db-archive" -> require(checkpoint, failures,
                "backup_id", "flyway_version");
            case "object-storage" -> require(checkpoint, failures,
                "inventory_time", "checksum_manifest");
            case "messaging" -> requireAny(checkpoint, failures,
                "stream_sequence", "consumer_offset");
            default -> {
                if (isBlank(checkpoint.checkpointType())) {
                    failures.add(checkpoint.component() + " checkpoint missing checkpoint_type");
                }
            }
        }
        return new ValidationResult(false, failures, java.util.List.of(), false, Map.of());
    }

    private static void require(MigrationCheckpoint checkpoint, ArrayList<String> failures, String... keys) {
        for (var key : keys) {
            if (isBlank(checkpoint.markers().get(key))) {
                failures.add(checkpoint.component() + " checkpoint missing " + key);
            }
        }
    }

    private static void requireAny(MigrationCheckpoint checkpoint, ArrayList<String> failures, String... keys) {
        for (var key : keys) {
            if (!isBlank(checkpoint.markers().get(key))) {
                return;
            }
        }
        failures.add(checkpoint.component() + " checkpoint missing " + String.join("_or_", keys));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
