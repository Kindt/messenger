package com.avandocmsg.messenger.api.platform.stack;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
            Map.of(),
            "postgres-16-bundled",
            "PT2H"
        );

        var result = MigrationCheckpointValidator.validate(checkpoint);

        assertFalse(result.passed());
        assertTrue(result.failures().contains("relational-db-hot checkpoint missing backup_id"));
        assertTrue(result.failures().contains("relational-db-hot checkpoint missing flyway_version"));
        assertTrue(result.failures().contains("relational-db-hot checkpoint missing wal_lsn"));
    }

    @Test
    void acceptsS3AndNatsCheckpointsWithRequiredMarkers() {
        var s3 = new MigrationCheckpoint(
            "object-storage",
            "s3-minio-bundled",
            "s3-compatible-external",
            "s3_inventory",
            Map.of(
                "inventory_time", "2026-06-20T20:00:00Z",
                "object_cursor", "files/2026/06/",
                "checksum_manifest", "sha256-manifest.json"
            ),
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

    @Test
    void searchReindexCheckpointRequiresCursorVersionAndShadowTarget() {
        var invalid = new MigrationCheckpoint(
            "search",
            "solr-bundled",
            "opensearch-candidate",
            "reindex",
            Map.of("reindex_cursor", "messages:42"),
            "solr-bundled",
            "PT4H"
        );

        var invalidResult = MigrationCheckpointValidator.validate(invalid);
        assertFalse(invalidResult.passed());
        assertTrue(invalidResult.failures().contains("search checkpoint missing index_schema_version"));
        assertTrue(invalidResult.failures().contains("search checkpoint missing shadow_target"));

        var valid = new MigrationCheckpoint(
            "search",
            "sql-search",
            "solr-bundled",
            "reindex",
            Map.of(
                "reindex_cursor", "messages:42",
                "index_schema_version", "v1",
                "shadow_target", "solr-shadow"
            ),
            "sql-search",
            "PT4H"
        );

        assertTrue(MigrationCheckpointValidator.validate(valid).passed());
    }

    @Test
    void checkpointReportExposesMissingMarkersRollbackAndNoSilentFallbackReadiness() {
        var checkpoint = new MigrationCheckpoint(
            "search",
            "sql-search",
            "opensearch-candidate",
            "reindex",
            Map.of("reindex_cursor", "messages:42"),
            "",
            "PT4H"
        );

        var report = MigrationCheckpointValidator.report(checkpoint);

        assertFalse(report.passed());
        assertEquals("search", report.component());
        assertEquals("blocker", report.severity());
        assertFalse(report.rollbackReady());
        assertTrue(report.noSilentFallback());
        assertTrue(report.missingMarkers().contains("index_schema_version"));
        assertTrue(report.missingMarkers().contains("shadow_target"));
    }

    @Test
    void compatibilityPacksExposeSupportedEvidenceAndCandidateBoundaries() {
        var packs = ConnectorCompatibilityPacks.catalog();

        var postgres = ConnectorCompatibilityPacks.packFor("postgres-16-external");
        assertEquals("relational-db-hot", postgres.component());
        assertTrue(postgres.requiredChecks().contains("flyway_privileges"));
        assertTrue(postgres.promotionEvidence().contains("h2_or_lab_migration_green"));
        assertEquals(LifecycleStatus.SUPPORTED_EXTERNAL_BYO, postgres.lifecycleStatus());
        assertTrue(postgres.supported());

        var minio = ConnectorCompatibilityPacks.packFor("s3-minio-bundled");
        assertTrue(minio.supported());
        assertTrue(minio.requiredChecks().contains("checksum"));

        var opensearch = ConnectorCompatibilityPacks.packFor("opensearch-candidate");
        assertEquals(LifecycleStatus.INTEGRATION_CANDIDATE, opensearch.lifecycleStatus());
        assertFalse(opensearch.supported());
        assertTrue(opensearch.unsupportedModes().contains("production_without_reindex_gate"));

        assertTrue(packs.stream().anyMatch(p -> p.profileId().equals("vks-integration-candidate")));
        assertTrue(packs.stream().anyMatch(p -> p.profileId().equals("dlp-external")));
        assertTrue(packs.stream().anyMatch(p -> p.profileId().equals("integrations-bundled")));
    }

    @Test
    void compatibilityPacksLoadFullYamlProfileCatalog() {
        var jatoba = ConnectorCompatibilityPacks.packFor("jatoba");
        assertEquals("relational-db-hot", jatoba.component());
        assertEquals(LifecycleStatus.CANDIDATE, jatoba.lifecycleStatus());
        assertTrue(jatoba.requiredChecks().contains("jdbc_connectivity"));
        assertTrue(jatoba.unsupportedModes().contains("supported_bundled_claim"));

        var nginx = ConnectorCompatibilityPacks.packFor("nginx-bundled");
        assertEquals("web-edge", nginx.component());
        assertEquals(LifecycleStatus.SUPPORTED_BUNDLED, nginx.lifecycleStatus());
        assertTrue(nginx.requiredChecks().contains("security_headers"));

        var livekit = ConnectorCompatibilityPacks.packFor("livekit-1.8-bundled");
        assertEquals("media", livekit.component());
        assertTrue(livekit.promotionEvidence().contains("korus_bundled_runbook"));
    }

    @Test
    void compatibilityPackYamlCatalogIsPackagedAsRuntimeResource() {
        assertNotNull(ConnectorCompatibilityPacks.class.getResource("/external-stack-profiles.yaml"));
    }
}
