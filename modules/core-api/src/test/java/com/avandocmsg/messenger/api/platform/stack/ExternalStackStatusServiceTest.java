package com.avandocmsg.messenger.api.platform.stack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalStackStatusServiceTest {

    @Test
    void resourceScaffoldReturnsEmptySafeBaseline() {
        var resource = new ExternalStackStatusResource();

        assertTrue(resource.status().components().isEmpty());
        assertTrue(resource.profiles().profiles().isEmpty());
    }

    @Test
    void rendersDesiredObservedMismatchAndSupportBoundary() {
        var desired = manifest("object-storage", "minio-s3", ExternalStackRole.active)
            .withEndpoint("https://minio.internal/deep-archive");
        var observed = manifest("object-storage", "external-s3", ExternalStackRole.active)
            .withEndpoint("https://token:secret@s3.customer.test/deep-archive");
        var validation = ExternalStackManifestValidator.validateDesiredManifests(List.of(observed));

        var status = new ExternalStackStatusService().status(List.of(new ManifestObservation(
            desired,
            observed,
            "degraded",
            "observed connector mismatch",
            validation
        )));

        var component = status.components().get("object-storage");
        assertEquals("minio-s3", component.desiredConnector());
        assertEquals("external-s3", component.observedConnector());
        assertEquals("degraded", component.healthStatus());
        assertEquals("observed connector mismatch", component.degradedReason());
        assertEquals("connector-validation", component.supportBoundary());
        assertTrue(component.mismatch());
        assertFalse(component.observedEndpoint().contains("secret"));
        assertEquals("https://<redacted>@s3.customer.test/deep-archive", component.observedEndpoint());
        assertEquals("passed", component.validationStatus());
        assertTrue(component.validationFailures().isEmpty());
    }

    @Test
    void exposesValidationFailuresWithoutLeakingSecretEndpoint() {
        var desired = manifest("idp", "oidc-generic", ExternalStackRole.active)
            .withEndpoint("https://user:secret@idp.example.test/realms/korus");
        var observed = manifest("idp", "oidc-generic", ExternalStackRole.migration_target)
            .withEndpoint("https://user:secret@idp.example.test/realms/korus");
        var validation = ExternalStackManifestValidator.validateDesiredManifests(List.of(observed));

        var status = new ExternalStackStatusService().status(List.of(new ManifestObservation(
            desired,
            observed,
            "degraded",
            "manifest validation failed",
            validation
        )));

        var component = status.components().get("idp");
        assertEquals("failed", component.validationStatus());
        assertTrue(component.validationFailures().contains("component idp has no active manifest"));
        assertFalse(component.observedEndpoint().contains("secret"));
        assertEquals("https://<redacted>@idp.example.test/realms/korus", component.observedEndpoint());
    }

    @Test
    void rendersCandidateLifecycleWithoutMarkingSupported() {
        var profile = new ConnectorProfile(
            "angie",
            "reverse-proxy",
            "nginx-compatible",
            LifecycleStatus.candidate,
            List.of(DeploymentMode.rf_candidate),
            List.of("websocket_upgrade"),
            "web-edge",
            SupportBoundary.externalByo("vendor"),
            null
        );

        var status = new ExternalStackStatusService().profileStatus(List.of(profile));

        assertEquals("candidate", status.profiles().get("angie").lifecycleStatus());
        assertFalse(status.profiles().get("angie").supported());
    }

    @Test
    void profileStatusIncludesCompatibilityPackEvidence() {
        var profile = new ConnectorProfile(
            "postgres-16-external",
            "postgres",
            "postgres-16",
            LifecycleStatus.supported_external_byo,
            List.of(DeploymentMode.external_byo),
            List.of("runtime_manifest"),
            "relational-db-hot",
            SupportBoundary.externalByo("customer"),
            null
        );

        var status = new ExternalStackStatusService().profileStatus(List.of(profile));
        var row = status.profiles().get("postgres-16-external");

        assertTrue(row.requiredChecks().contains("flyway_privileges"));
        assertTrue(row.promotionEvidence().contains("customer_backup_and_wal_evidence"));
        assertTrue(row.unsupportedModes().contains("silent_fallback"));
    }

    @Test
    void resourcePreflightCheckpointReturnsStructuredReport() {
        var resource = new ExternalStackStatusResource();
        var checkpoint = new MigrationCheckpoint(
            "search",
            "sql-search",
            "opensearch-candidate",
            "reindex",
            Map.of("reindex_cursor", "messages:42"),
            "",
            "PT4H"
        );

        var report = resource.preflightCheckpoint(checkpoint);

        assertFalse(report.passed());
        assertEquals("search", report.component());
        assertEquals("blocker", report.severity());
        assertTrue(report.missingMarkers().contains("index_schema_version"));
        assertTrue(report.noSilentFallback());
    }

    @Test
    void resourcePreflightManifestsReturnsRedactedValidationResult() {
        var resource = new ExternalStackStatusResource();
        var primary = manifest("object-storage", "minio-s3", ExternalStackRole.active)
            .withEndpoint("https://user:secret@s3-a.example.test/files");
        var secondActive = manifest("object-storage", "external-s3", ExternalStackRole.active)
            .withEndpoint("https://token:secret@s3-b.example.test/files");

        var result = resource.preflightManifests(new ExternalStackManifestPreflightRequest(List.of(primary, secondActive)));

        assertFalse(result.passed());
        assertTrue(result.failures().contains("component object-storage has 2 active manifests"));
        assertTrue(result.redacted());
        assertFalse(result.metadata().get("object-storage.endpoint").contains("secret"));
    }

    @Test
    void resourceExposesFullCompatibilityPackCatalog() {
        var resource = new ExternalStackStatusResource();

        var catalog = resource.compatibilityPacks();

        assertTrue(catalog.packs().containsKey("postgres-16-external"));
        assertTrue(catalog.packs().containsKey("opensearch-candidate"));
        assertEquals("search", catalog.packs().get("opensearch-candidate").component());
        assertFalse(catalog.packs().get("opensearch-candidate").supported());
        assertTrue(catalog.packs().get("opensearch-candidate").unsupportedModes()
            .contains("production_without_reindex_gate"));
    }

    private static ComponentBackendManifest manifest(String component, String connector, ExternalStackRole role) {
        return new ComponentBackendManifest(
            component,
            "family",
            connector,
            "1",
            role,
            "https://example.test/" + component,
            component + "-resource",
            "v1",
            "explicit",
            "single-node",
            "test-revision",
            List.of("health"),
            "test-data",
            SupportBoundary.externalByo("customer"),
            Map.of("serve_traffic", "true")
        );
    }
}
