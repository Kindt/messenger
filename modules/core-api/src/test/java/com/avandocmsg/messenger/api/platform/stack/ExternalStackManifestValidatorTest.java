package com.avandocmsg.messenger.api.platform.stack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalStackManifestValidatorTest {

    @Test
    void rejectsTwoActiveManifestsForSameComponent() {
        var manifests = List.of(
            activeManifest("relational-db-hot", "postgres-16"),
            activeManifest("relational-db-hot", "postgres-pro")
        );

        var result = ExternalStackManifestValidator.validateDesiredManifests(manifests);

        assertFalse(result.passed());
        assertTrue(result.failures().contains("component relational-db-hot has 2 active manifests"));
    }

    @Test
    void rejectsTrafficRoleWhenManifestIsNotActive() {
        var manifests = List.of(activeManifest("messaging", "nats-2.10").withRole(ExternalStackRole.MIGRATION_TARGET));

        var result = ExternalStackManifestValidator.validateDesiredManifests(manifests);

        assertFalse(result.passed());
        assertTrue(result.failures().contains("component messaging has no active manifest"));
        assertTrue(result.failures().contains("component messaging role migration_target cannot serve active traffic"));
    }

    @Test
    void rejectsAmbiguousProductionAutoProfile() {
        var manifest = activeManifest("object-storage", "s3-compatible")
            .withCompatibilityProfile("auto")
            .withCapabilities(List.of("endpoint:minio", "endpoint:external-s3"));

        var result = ExternalStackManifestValidator.validateDesiredManifests(List.of(manifest));

        assertFalse(result.passed());
        assertTrue(result.failures().contains("component object-storage uses ambiguous production auto profile"));
    }

    @Test
    void rejectsUnknownCompatibilityProfile() {
        var manifest = activeManifest("object-storage", "external-s3")
            .withCompatibilityProfile("missing-s3-profile");

        var result = ExternalStackManifestValidator.validateDesiredManifests(List.of(manifest));

        assertFalse(result.passed());
        assertTrue(result.failures()
            .contains("component object-storage references unknown compatibility profile missing-s3-profile"));
    }

    @Test
    void rejectsCompatibilityProfileFromDifferentComponent() {
        var manifest = activeManifest("object-storage", "external-s3")
            .withCompatibilityProfile("opensearch-candidate");

        var result = ExternalStackManifestValidator.validateDesiredManifests(List.of(manifest));

        assertFalse(result.passed());
        assertTrue(result.failures()
            .contains("component object-storage profile opensearch-candidate belongs to component search"));
    }

    @Test
    void rejectsActiveManifestUsingCandidateProfile() {
        var manifest = activeManifest("search", "opensearch")
            .withCompatibilityProfile("opensearch-candidate");

        var result = ExternalStackManifestValidator.validateDesiredManifests(List.of(manifest));

        assertFalse(result.passed());
        assertTrue(result.failures()
            .contains("component search profile opensearch-candidate is not production-supported"));
    }

    @Test
    void warnsForActiveExternalByoProfileUnsupportedModes() {
        var manifest = activeManifest("relational-db-hot", "postgres-16")
            .withCompatibilityProfile("postgres-16-external")
            .withMetadata(Map.of("serve_traffic", "true"));

        var result = ExternalStackManifestValidator.validateDesiredManifests(List.of(manifest));

        assertTrue(result.passed());
        assertTrue(result.warnings()
            .contains("component relational-db-hot profile postgres-16-external requires customer support boundary evidence"));
        assertTrue(result.warnings()
            .contains("component relational-db-hot profile postgres-16-external unsupported mode: silent_fallback"));
    }

    @Test
    void warnsWhenActiveManifestDoesNotProvideRequiredCheckEvidence() {
        var manifest = activeManifest("object-storage", "minio-s3")
            .withCompatibilityProfile("s3-minio-bundled")
            .withCapabilities(List.of("put_get_head_delete_list"));

        var result = ExternalStackManifestValidator.validateDesiredManifests(List.of(manifest));

        assertTrue(result.passed());
        assertTrue(result.warnings()
            .contains("component object-storage missing required check evidence: bucket_policy"));
        assertTrue(result.warnings()
            .contains("component object-storage missing required check evidence: multipart"));
    }

    @Test
    void rejectsCandidateAsSupportedBundled() {
        var profile = new ConnectorProfile(
            "angie",
            "reverse-proxy",
            "nginx-compatible",
            LifecycleStatus.CANDIDATE,
            List.of(DeploymentMode.BUNDLED),
            List.of("websocket_upgrade"),
            "web-edge",
            SupportBoundary.externalByo("customer"),
            null
        );

        var result = ExternalStackManifestValidator.validateProfiles(List.of(profile));

        assertFalse(result.passed());
        assertTrue(result.failures().contains("profile angie is candidate but declares bundled deployment"));
    }

    @Test
    void rejectsPromotionWithoutImpactModel() {
        var profile = new ConnectorProfile(
            "postgres-16-external",
            "postgres",
            "postgres-16",
            LifecycleStatus.SUPPORTED_EXTERNAL_BYO,
            List.of(DeploymentMode.EXTERNAL_BYO),
            List.of("jdbc_connectivity", "flyway_privileges"),
            "pg",
            SupportBoundary.externalByo("customer-dba"),
            null
        );

        var result = ExternalStackManifestValidator.validateProfiles(List.of(profile));

        assertFalse(result.passed());
        assertTrue(result.failures().contains("profile postgres-16-external is supported but has no impact model"));
    }

    @Test
    void acceptsSupportedExternalProfileWithImpactModelAndSingleActiveManifest() {
        var profile = new ConnectorProfile(
            "postgres-16-external",
            "postgres",
            "postgres-16",
            LifecycleStatus.SUPPORTED_EXTERNAL_BYO,
            List.of(DeploymentMode.EXTERNAL_BYO),
            List.of("jdbc_connectivity", "flyway_privileges"),
            "pg",
            SupportBoundary.externalByo("customer-dba"),
            new ImpactModel("bounded", "customer-ha", "db-sized", "customer-tco", "dba-owned")
        );
        var manifest = activeManifest("relational-db-hot", "postgres-16")
            .withResourceNameOrAlias("avandocmsg_hot");

        assertTrue(ExternalStackManifestValidator.validateProfiles(List.of(profile)).passed());
        assertTrue(ExternalStackManifestValidator.validateDesiredManifests(List.of(manifest)).passed());
    }

    @Test
    void redactsSecretBearingEndpointInValidationResult() {
        var manifest = activeManifest("idp", "oidc-generic")
            .withEndpoint("https://user:secret@example.test/realms/korus");

        var result = ExternalStackManifestValidator.validateDesiredManifests(List.of(manifest));

        assertTrue(result.redacted());
        assertEquals("https://<redacted>@example.test/realms/korus", result.metadata().get("idp.endpoint"));
    }

    @Test
    void acceptsManifestWithoutEndpointWhenResourceAliasIsExplicit() {
        var manifest = activeManifest("messaging", "nats-2.10")
            .withEndpoint(null)
            .withResourceNameOrAlias("msg");

        var result = ExternalStackManifestValidator.validateDesiredManifests(List.of(manifest));

        assertTrue(result.passed());
        assertFalse(result.redacted());
    }

    private static ComponentBackendManifest activeManifest(String component, String connector) {
        return new ComponentBackendManifest(
            component,
            "family",
            connector,
            "1",
            ExternalStackRole.ACTIVE,
            "https://example.test/" + component,
            component + "-resource",
            "v1",
            "explicit",
            "single-node",
            "test-revision",
            List.of("health"),
            "test-data",
            SupportBoundary.bundled("korus"),
            Map.of("serve_traffic", "true")
        );
    }
}
