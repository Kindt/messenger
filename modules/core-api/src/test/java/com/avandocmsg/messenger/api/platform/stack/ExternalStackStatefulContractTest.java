package com.avandocmsg.messenger.api.platform.stack;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalStackStatefulContractTest {

    @Test
    void pgContractRequiresJdbcFlywayPoolAndFailClosed() {
        var contract = ExternalStackComponentContracts.contractFor("relational-db-hot");

        assertEquals("fail_closed", contract.failurePolicy());
        assertTrue(contract.requiredChecks().contains("jdbc_connectivity"));
        assertTrue(contract.requiredChecks().contains("flyway_privileges"));
        assertTrue(contract.requiredChecks().contains("pool_sizing"));
    }

    @Test
    void objectStorageContractRequiresMultipartChecksumAndNoPurgeSafety() {
        var contract = ExternalStackComponentContracts.contractFor("object-storage");

        assertEquals("uploads_controlled_error_no_purge_without_snapshot", contract.failurePolicy());
        assertTrue(contract.requiredChecks().contains("multipart"));
        assertTrue(contract.requiredChecks().contains("checksum"));
        assertTrue(contract.requiredChecks().contains("lifecycle_object_lock"));
    }

    @Test
    void natsContractRequiresSubjectsJetStreamAndDrainChecks() {
        var contract = ExternalStackComponentContracts.contractFor("messaging");

        assertEquals("workers_pause_no_silent_fallback", contract.failurePolicy());
        assertTrue(contract.requiredChecks().contains("publish_subscribe_subject_prefixes"));
        assertTrue(contract.requiredChecks().contains("jetstream_if_required"));
        assertTrue(contract.requiredChecks().contains("drain_behavior"));
    }

    @Test
    void rejectsPgCheckpointWithoutBackupAndFlywayVersion() {
        var checkpoint = new MigrationCheckpoint(
            "relational-db-hot",
            "postgres-16-bundled",
            "postgres-16-external",
            "pg",
            Map.of("wal_lsn", "0/16B6C50"),
            "postgres-16-bundled",
            "PT2H"
        );

        var result = MigrationCheckpointValidator.validate(checkpoint);

        assertFalse(result.passed());
        assertTrue(result.failures().contains("relational-db-hot checkpoint missing backup_id"));
        assertTrue(result.failures().contains("relational-db-hot checkpoint missing flyway_version"));
    }

    @Test
    void acceptsS3AndNatsCheckpointsWithRequiredMarkers() {
        var s3 = new MigrationCheckpoint(
            "object-storage",
            "s3-minio-bundled",
            "s3-compatible-external",
            "s3_inventory",
            Map.of("inventory_time", "2026-06-20T20:00:00Z", "checksum_manifest", "sha256-manifest.json"),
            "s3-minio-bundled",
            "PT24H"
        );
        var nats = new MigrationCheckpoint(
            "messaging",
            "nats-2.10-bundled",
            "nats-2.x-external",
            "jetstream",
            Map.of("stream_sequence", "42", "consumer_offset", "41"),
            "nats-2.10-bundled",
            "PT1H"
        );

        assertTrue(MigrationCheckpointValidator.validate(s3).passed());
        assertTrue(MigrationCheckpointValidator.validate(nats).passed());
    }
}
