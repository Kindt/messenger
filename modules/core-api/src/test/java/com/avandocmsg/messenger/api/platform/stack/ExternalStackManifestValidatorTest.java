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
        var manifests = List.of(activeManifest("messaging", "nats-2.10").withRole(ExternalStackRole.migration_target));

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
    void rejectsCandidateAsSupportedBundled() {
        var profile = new ConnectorProfile(
            "angie",
            "reverse-proxy",
            "nginx-compatible",
            LifecycleStatus.candidate,
            List.of(DeploymentMode.bundled),
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
            LifecycleStatus.supported_external_byo,
            List.of(DeploymentMode.external_byo),
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
            LifecycleStatus.supported_external_byo,
            List.of(DeploymentMode.external_byo),
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
            ExternalStackRole.active,
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
