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
