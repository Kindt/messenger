package com.avandocmsg.messenger.api.platform.stack;

import com.avandocmsg.messenger.api.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;

import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalStackRuntimeManifestProviderTest {

    @Test
    void buildsConfiguredManifestsForCoreAndAddonExternalStack() {
        var provider = new ExternalStackRuntimeManifestProvider(new TestConfig());

        var observations = provider.observations();

        assertEquals(11, observations.size());
        assertTrue(observations.stream().anyMatch(o -> o.desiredManifest().component().equals("relational-db-hot")));
        assertTrue(observations.stream().anyMatch(o -> o.desiredManifest().component().equals("object-storage")));
        assertTrue(observations.stream().anyMatch(o -> o.desiredManifest().component().equals("messaging")));
        assertTrue(observations.stream().anyMatch(o -> o.desiredManifest().component().equals("idp")));
        assertTrue(observations.stream().anyMatch(o -> o.desiredManifest().component().equals("cache")));
        assertTrue(observations.stream().anyMatch(o -> o.desiredManifest().component().equals("web-edge")));
        assertTrue(observations.stream().anyMatch(o -> o.desiredManifest().component().equals("media")));
        assertTrue(observations.stream().anyMatch(o -> o.desiredManifest().component().equals("turn")));
        assertTrue(observations.stream().anyMatch(o -> o.desiredManifest().component().equals("notifications")));
        assertTrue(observations.stream().anyMatch(o -> o.desiredManifest().component().equals("dlp")));
        assertTrue(observations.stream().anyMatch(o -> o.desiredManifest().component().equals("integrations")));
        assertTrue(observations.stream().allMatch(o -> o.validationResult().passed()));
    }

    @Test
    void addonManifestsReflectConfiguredCapabilitiesWithoutSecrets() {
        var provider = new ExternalStackRuntimeManifestProvider(new TestConfig());

        var observations = provider.observations();

        var media = observation(observations, "media").desiredManifest();
        assertEquals("livekit", media.backendFamily());
        assertTrue(media.capabilities().contains("livekit_token_issue"));

        var turn = observation(observations, "turn").desiredManifest();
        assertEquals("stun-turn", turn.backendFamily());
        assertTrue(turn.endpoint().contains("stun:turn.example.test:3478"));

        var notifications = observation(observations, "notifications").desiredManifest();
        assertTrue(notifications.capabilities().contains("vapid_config"));
        assertFalse(notifications.endpoint().contains("private"));

        var dlp = observation(observations, "dlp").desiredManifest();
        assertEquals("dlp-integration", dlp.backendFamily());
        assertTrue(dlp.capabilities().contains("e2ee_plaintext_boundary"));
    }

    @Test
    void statusResourceUsesRuntimeProviderAndRedactsSecretEndpoints() {
        var resource = new ExternalStackStatusResource(new ExternalStackRuntimeManifestProvider(new TestConfig()));

        var status = resource.status();

        var db = status.components().get("relational-db-hot");
        assertNotNull(db);
        assertEquals("postgres-16", db.desiredConnector());
        assertFalse(db.observedEndpoint().contains("secret"));
        assertTrue(db.observedEndpoint().contains("<redacted>"));
    }

    @Test
    void providerExposesSupportedBundledAndExternalProfiles() {
        var provider = new ExternalStackRuntimeManifestProvider(new TestConfig());

        var profiles = provider.profiles();

        assertTrue(profiles.stream().anyMatch(p -> p.profileId().equals("postgres-16-bundled")));
        assertTrue(profiles.stream().anyMatch(p -> p.profileId().equals("s3-minio-bundled")));
        assertTrue(profiles.stream().anyMatch(p -> p.profileId().equals("livekit-bundled")));
        assertTrue(profiles.stream().anyMatch(p -> p.profileId().equals("dlp-external")));
        assertTrue(profiles.stream().anyMatch(p -> p.lifecycleStatus() == LifecycleStatus.supported_external_byo));
    }

    @Test
    void vksProfileRemainsIntegrationCandidateNotSupported() {
        var provider = new ExternalStackRuntimeManifestProvider(new TestConfig());

        var profileStatus = new ExternalStackStatusService().profileStatus(provider.profiles());

        var vks = profileStatus.profiles().get("vks-integration-candidate");
        assertNotNull(vks);
        assertEquals("integration_candidate", vks.lifecycleStatus());
        assertFalse(vks.supported());
    }

    @Test
    void loadsDesiredManifestsFromConfiguredYamlPath() throws Exception {
        var manifest = Files.createTempFile("external-stack-manifest", ".yaml");
        Files.writeString(manifest, """
            manifests:
              - component: relational-db-hot
                backend_family: postgres
                connector: postgres-16-external
                version: "16"
                role: active
                endpoint: jdbc:postgresql://user:secret@db.example.test:5432/avandocmsg_hot
                resource_name_or_alias: avandocmsg_hot
                schema_or_protocol_version: flyway-current
                compatibility_profile: postgres-16-external
                topology: external_byo
                config_revision: ansible-test
                capabilities: [jdbc_connectivity, flyway_privileges]
                data_classification: hot-personal-data
                support_boundary:
                  deployment_owner: customer
                  backup_owner: customer
                  ha_owner: customer
                  upgrade_owner: customer
                  incident_owner: customer
                  vendor_support_required: true
                  korus_support_scope: connector-validation
                metadata:
                  serve_traffic: "true"
            """);

        var provider = new ExternalStackRuntimeManifestProvider(new TestConfig() {
            @Override
            public String externalStackManifestPath() {
                return manifest.toString();
            }
        });

        var status = new ExternalStackStatusService().status(provider.observations());
        var db = status.components().get("relational-db-hot");

        assertEquals("postgres-16-external", db.desiredConnector());
        assertEquals("connector-validation", db.supportBoundary());
        assertFalse(db.observedEndpoint().contains("secret"));
    }

    @Test
    void activeProbeResultsDecorateManifestObservations() {
        var provider = new ExternalStackRuntimeManifestProvider(
            new TestConfig(),
            ExternalStackActiveProbeService.of(Map.of(
                "relational-db-hot",
                manifest -> ExternalStackProbeResult.degraded("jdbc privileges missing", "flyway_privileges")
            ))
        );

        var db = observation(provider.observations(), "relational-db-hot");

        assertEquals("degraded", db.healthStatus());
        assertEquals("jdbc privileges missing", db.degradedReason());
        assertTrue(db.validationResult().warnings().contains("relational-db-hot probe warning: flyway_privileges"));
        assertFalse(db.validationResult().metadata().values().stream().anyMatch(v -> v.contains("secret")));
    }

    @Test
    void boundedActiveProbesCheckJdbcMetadataRedisAndUrlShapes() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:external_stack_probe;DB_CLOSE_DELAY=-1");
        var provider = new ExternalStackRuntimeManifestProvider(
            new TestConfig(),
            ExternalStackActiveProbeService.bounded(new TestConfig(), ds, () -> false)
        );

        var db = observation(provider.observations(), "relational-db-hot");
        var cache = observation(provider.observations(), "cache");
        var idp = observation(provider.observations(), "idp");
        var web = observation(provider.observations(), "web-edge");

        assertEquals("healthy", db.healthStatus());
        assertTrue(db.validationResult().metadata().containsKey("relational-db-hot.database_product"));
        assertEquals("degraded", cache.healthStatus());
        assertEquals("redis ping failed", cache.degradedReason());
        assertEquals("healthy", idp.healthStatus());
        assertEquals("healthy", web.healthStatus());
    }

    @Test
    void boundedActiveProbesUseAttachedS3AndNatsSuppliersWhenProvided() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:external_stack_probe_attached;DB_CLOSE_DELAY=-1");
        var provider = new ExternalStackRuntimeManifestProvider(
            new TestConfig(),
            ExternalStackActiveProbeService.bounded(new TestConfig(), ds, () -> true, () -> true, () -> false)
        );

        var storage = observation(provider.observations(), "object-storage");
        var messaging = observation(provider.observations(), "messaging");

        assertEquals("healthy", storage.healthStatus());
        assertEquals("degraded", messaging.healthStatus());
        assertEquals("nats client disconnected", messaging.degradedReason());
    }

    private static ManifestObservation observation(
        java.util.List<ManifestObservation> observations,
        String component
    ) {
        return observations.stream()
            .filter(o -> o.desiredManifest().component().equals(component))
            .findFirst()
            .orElseThrow();
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
