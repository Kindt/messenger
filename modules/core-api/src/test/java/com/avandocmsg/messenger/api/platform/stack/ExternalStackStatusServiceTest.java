package com.avandocmsg.messenger.api.platform.stack;

import com.avandocmsg.messenger.api.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        assertTrue(row.promotionEvidence().contains("h2_or_lab_migration_green"));
        assertTrue(row.promotionEvidence().contains("customer_profile_evidence"));
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
    void resourcePreflightManifestsReportExplainsSeverityByComponent() {
        var resource = new ExternalStackStatusResource();
        var primary = manifest("object-storage", "minio-s3", ExternalStackRole.active)
            .withEndpoint("https://user:secret@minio.example.test/bucket");
        var secondActive = manifest("object-storage", "external-s3", ExternalStackRole.active);
        var searchServingStandby = manifest("search", "opensearch", ExternalStackRole.migration_target)
            .withMetadata(Map.of("serve_traffic", "true"));

        var report = resource.preflightManifestReport(
            new ExternalStackManifestPreflightRequest(List.of(primary, secondActive, searchServingStandby))
        );

        assertFalse(report.validation().passed());
        assertEquals("blocked", report.severity());
        assertEquals(2, report.components().get("object-storage").manifestCount());
        assertEquals(2, report.components().get("object-storage").activeCount());
        assertTrue(report.components().get("object-storage").failures()
            .contains("component object-storage has 2 active manifests"));
        assertTrue(report.components().get("search").failures()
            .contains("component search role migration_target cannot serve active traffic"));
        assertEquals("https://<redacted>@minio.example.test/bucket",
            report.components().get("object-storage").redactedEndpoint());
        assertTrue(report.components().get("object-storage").missingRequiredChecks().contains("bucket_policy"));
        assertTrue(report.failureCount() >= 2);
        assertTrue(report.warningCount() >= 1);
        assertTrue(report.missingRequiredCheckCount() >= 1);
        assertTrue(report.remediationActions().contains("object-storage: keep exactly one active manifest"));
        assertTrue(report.components().get("object-storage").remediationActions()
            .contains("provide evidence for required check bucket_policy"));
        assertTrue(report.components().get("search").remediationActions()
            .contains("disable serve_traffic for non-active role"));
    }

    @Test
    void resourcePreflightManifestsReportUsesWarningSeverityForWarningOnlyValidation() {
        var resource = new ExternalStackStatusResource();
        var manifest = manifest("object-storage", "minio-s3", ExternalStackRole.active)
            .withCompatibilityProfile("s3-minio-bundled")
            .withCapabilities(List.of("put_get_head_delete_list"));

        var report = resource.preflightManifestReport(new ExternalStackManifestPreflightRequest(List.of(manifest)));

        assertTrue(report.passed());
        assertEquals("warning", report.severity());
        assertEquals(0, report.failureCount());
        assertTrue(report.warningCount() > 0);
        assertTrue(report.missingRequiredCheckCount() > 0);
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

    @Test
    void resourceExposesSingleCompatibilityPackAndProfilePreflight() {
        var resource = new ExternalStackStatusResource();

        var pack = resource.compatibilityPack("opensearch-candidate");
        var validation = resource.preflightProfile(new ExternalStackProfilePreflightRequest("opensearch-candidate"));

        assertEquals("search", pack.component());
        assertFalse(pack.supported());
        assertFalse(validation.passed());
        assertTrue(validation.failures().contains("profile opensearch-candidate is not production-supported"));
    }

    @Test
    void resourcePreflightProfileReportExplainsPromotionEvidence() {
        var resource = new ExternalStackStatusResource();

        var supported = resource.preflightProfileReport(new ExternalStackProfilePreflightRequest(
            "postgres-16-external",
            List.of("h2_or_lab_migration_green")
        ));
        var candidate = resource.preflightProfileReport(new ExternalStackProfilePreflightRequest(
            "opensearch-candidate",
            List.of("search_reindex_contract_green")
        ));

        assertTrue(supported.passed());
        assertEquals("warning", supported.severity());
        assertEquals(1, supported.missingPromotionEvidenceCount());
        assertEquals(1, supported.unsupportedModeCount());
        assertTrue(supported.missingPromotionEvidence().contains("customer_profile_evidence"));
        assertTrue(supported.unsupportedModes().contains("silent_fallback"));
        assertTrue(supported.remediationActions().contains("postgres-16-external: attach promotion evidence customer_profile_evidence"));
        assertTrue(supported.remediationActions().contains("postgres-16-external: remove unsupported mode silent_fallback"));

        assertFalse(candidate.passed());
        assertEquals("blocked", candidate.severity());
        assertEquals(1, candidate.missingPromotionEvidenceCount());
        assertTrue(candidate.failures().contains("profile opensearch-candidate is not production-supported"));
        assertTrue(candidate.missingPromotionEvidence().contains("vendor_certification_required"));
        assertTrue(candidate.remediationActions().contains("opensearch-candidate: use a supported production profile"));
    }

    @Test
    void resourceExposesSingleComponentStatus() {
        var resource = new ExternalStackStatusResource(new ExternalStackRuntimeManifestProvider(new TestConfig()));

        var component = resource.componentStatus("object-storage");

        assertEquals("minio-s3", component.desiredConnector());
        assertEquals("passed", component.validationStatus());
    }

    @Test
    void resourceExposesComponentValidationContracts() {
        var resource = new ExternalStackStatusResource();

        var catalog = resource.componentContracts();
        var objectStorage = resource.componentContract("object-storage");

        assertTrue(catalog.contracts().containsKey("relational-db-hot"));
        assertTrue(catalog.contracts().containsKey("object-storage"));
        assertTrue(objectStorage.requiredChecks().contains("put_get_head_delete_list"));
        assertEquals("uploads_controlled_error_no_purge_without_snapshot", objectStorage.failurePolicy());
    }

    @Test
    void resourceExposesPassingCatalogHealthReport() {
        var resource = new ExternalStackStatusResource();

        var health = resource.catalogHealth();

        assertTrue(health.passed());
        assertTrue(health.componentCount() >= 10);
        assertTrue(health.profileCount() >= 25);
        assertTrue(health.failures().isEmpty());
        assertEquals(0, health.failureCount());
        assertEquals(1, health.warningCount());
        assertTrue(health.warnings().contains("candidate profiles require explicit promotion before production use"));
        assertTrue(health.remediationActions()
            .contains("promote, keep migration-only, or reject candidate profiles before production use"));
    }

    @Test
    void resourceExposesComponentProfileReadinessSummary() {
        var resource = new ExternalStackStatusResource();

        var catalog = resource.componentProfileSummary();
        var search = resource.componentProfileSummary("search");

        assertTrue(catalog.components().containsKey("object-storage"));
        assertTrue(search.profileCount() >= 3);
        assertTrue(search.supportedCount() >= 1);
        assertTrue(search.candidateCount() >= 2);
        assertEquals("candidate profiles require explicit promotion", search.readinessWarning());
        assertEquals("warning", search.readinessSeverity());
        assertTrue(search.remediationActions()
            .contains("search: promote, keep migration-only, or reject candidate profiles"));
    }

    @Test
    void resourceExposesLabCutoverReadinessReport() {
        var resource = new ExternalStackStatusResource();

        var report = resource.cutoverReadiness();

        assertEquals("repo-local-lab", report.environment());
        assertEquals("warning", report.severity());
        assertTrue(report.ready());
        assertTrue(report.warningCount() >= 1);
        assertTrue(report.smokeCommand().contains("smoke-external-stack-lab-cutover.ps1"));
        assertTrue(report.remediationActions()
            .contains("promote, keep migration-only, or reject candidate profiles before production use"));
        assertTrue(report.remediationActions()
            .contains("search: promote, keep migration-only, or reject candidate profiles"));
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

    private static class TestConfig extends AppConfig {
        @Override
        public String dbJdbcUrl() {
            return "jdbc:postgresql://user:secret@db.example.test:5432/avandocmsg_hot";
        }

        @Override
        public String minioEndpoint() {
            return "https://minio.example.test";
        }

        @Override
        public String minioBucket() {
            return "avandocmsg";
        }

        @Override
        public String natsUrl() {
            return "nats://nats.example.test:4222";
        }

        @Override
        public boolean natsJetstream() {
            return true;
        }

        @Override
        public String keycloakIssuer() {
            return "https://idp.example.test/realms/avandocmsg";
        }

        @Override
        public String redisUri() {
            return "redis://redis.example.test:6379";
        }

        @Override
        public String webPublicBaseUrl() {
            return "https://messenger.example.test/";
        }

        @Override
        public String livekitUrl() {
            return "wss://livekit.example.test";
        }

        @Override
        public String webrtcStunUris() {
            return "stun:turn.example.test:3478";
        }

        @Override
        public Optional<String> webClientVapidPublicKey() {
            return Optional.of("public-vapid-key");
        }

        @Override
        public String integrationsBaseUrl() {
            return "https://integrations.example.test";
        }
    }
}
