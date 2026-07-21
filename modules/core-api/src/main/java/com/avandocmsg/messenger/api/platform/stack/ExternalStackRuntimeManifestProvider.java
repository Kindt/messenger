package com.avandocmsg.messenger.api.platform.stack;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExternalStackRuntimeManifestProvider {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();

    private static final String FAMILY_POSTGRES = "postgres";
    private static final String CONNECTOR_POSTGRES_16 = "postgres-16";
    private static final String FAMILY_S3 = "s3-compatible";
    private static final String FAMILY_REDIS = "redis-compatible";
    private static final String PROTOCOL_REDIS = "redis";
    private static final String COMPONENT_MEDIA = "media";
    private static final String FAMILY_LIVEKIT = "livekit";
    private static final String PROTOCOL_HTTP_JSON = "http-json";
    private static final String UNKNOWN = "unknown";

    private final AppConfig appConfig;
    private final ExternalStackActiveProbeService activeProbeService;

    public ExternalStackRuntimeManifestProvider(AppConfig appConfig) {
        this(appConfig, ExternalStackActiveProbeService.none());
    }

    public ExternalStackRuntimeManifestProvider(
        AppConfig appConfig,
        ExternalStackActiveProbeService activeProbeService
    ) {
        this.appConfig = appConfig;
        this.activeProbeService = activeProbeService == null ? ExternalStackActiveProbeService.none() : activeProbeService;
    }

    public List<ManifestObservation> observations() {
        var configured = loadConfiguredManifests();
        if (!configured.isEmpty()) {
            return configured.stream().map(this::observation).toList();
        }
        return List.of(
            observation(manifest(new ManifestSpec(
                "relational-db-hot",
                FAMILY_POSTGRES,
                CONNECTOR_POSTGRES_16,
                appConfig.dbJdbcUrl(),
                "avandocmsg_hot",
                "flyway-current",
                "postgres-16-bundled",
                List.of("jdbc_connectivity", "flyway_privileges", "pool_sizing"),
                "hot-personal-data"
            ))),
            observation(manifest(new ManifestSpec(
                "object-storage",
                FAMILY_S3,
                "minio-s3",
                appConfig.minioEndpoint(),
                appConfig.minioBucket(),
                "s3",
                "s3-minio-bundled",
                List.of("put_get_head_delete_list", "multipart", "checksum"),
                "file-content"
            ))),
            observation(manifest(new ManifestSpec(
                "messaging",
                "nats",
                "nats-2.10",
                appConfig.natsUrl(),
                appConfig.natsJetstream() ? "jetstream" : "core-nats",
                appConfig.natsJetstream() ? "jetstream" : "core",
                "nats-2.10-bundled",
                List.of("publish_subscribe_subject_prefixes", "queue_groups", "drain_behavior"),
                "event-metadata"
            ))),
            observation(manifest(new ManifestSpec(
                "idp",
                "oidc",
                "keycloak-24",
                appConfig.keycloakIssuer(),
                "avandocmsg",
                "oidc",
                "keycloak-24-bundled",
                List.of("issuer_jwks_tls", "token_signature_audience_issuer", "required_claims_user_org_roles"),
                "identity-security"
            ))),
            observation(manifest(new ManifestSpec(
                "cache",
                FAMILY_REDIS,
                "redis-7",
                appConfig.redisUri(),
                "default",
                PROTOCOL_REDIS,
                "redis-7-bundled",
                List.of("command_subset_get_set_del_expire_counters_ttl", "key_prefix_isolation"),
                "cache-security-adjacent"
            ))),
            observation(manifest(new ManifestSpec(
                "web-edge",
                "http-reverse-proxy",
                "tomcat-11",
                appConfig.webPublicBaseUrl(),
                "public-web",
                "http",
                "nginx-bundled",
                List.of("health_routing", "forwarded_headers", "security_headers"),
                "public-edge"
            ))),
            observation(manifest(new ManifestSpec(
                COMPONENT_MEDIA,
                FAMILY_LIVEKIT,
                FAMILY_LIVEKIT,
                appConfig.livekitUrl(),
                appConfig.livekitUrl().isBlank() ? "mesh-webrtc" : FAMILY_LIVEKIT,
                FAMILY_LIVEKIT,
                "livekit-1.8-bundled",
                List.of("livekit_token_issue", "room_join", "vks_integration_candidate_gate"),
                "media-metadata"
            ))),
            observation(manifest(new ManifestSpec(
                "turn",
                "stun-turn",
                "webrtc-ice",
                appConfig.webrtcStunUris(),
                "ice",
                "webrtc",
                "explicit",
                List.of("realm_secret", "relay_reachability", "udp_tcp_ports"),
                "media-network"
            ))),
            observation(manifest(new ManifestSpec(
                "notifications",
                "web-push",
                "vapid",
                appConfig.webClientVapidPublicKey().orElse("not-configured"),
                "browser-push",
                "webpush",
                "webpush-vapid-bundled-config",
                List.of("vapid_config", "gateway_config", "best_effort_semantics"),
                "notification-metadata"
            ))),
            observation(manifest(new ManifestSpec(
                "dlp",
                "dlp-integration",
                "connector-runtime-dlp",
                appConfig.integrationsBaseUrl(),
                "dlp-policy",
                PROTOCOL_HTTP_JSON,
                "explicit",
                List.of("endpoint_auth_tls", "verdict_schema", "tenant_policy", "e2ee_plaintext_boundary"),
                "compliance-boundary"
            ))),
            observation(manifest(new ManifestSpec(
                "integrations",
                "connector-runtime",
                "webhook-plugin-bot-gateway",
                appConfig.integrationsBaseUrl(),
                "integrations-gateway",
                PROTOCOL_HTTP_JSON,
                "http-webhook-generic",
                List.of("webhook_plugin_bot_gateway_endpoint", "event_schema", "retry_timeout", "audit_boundary"),
                "integration-events"
            )))
        );
    }

    private List<ComponentBackendManifest> loadConfiguredManifests() {
        var path = appConfig.externalStackManifestPath();
        if (path == null || path.isBlank()) {
            return List.of();
        }
        var manifestPath = Path.of(path);
        if (!Files.isRegularFile(manifestPath)) {
            return List.of();
        }
        try {
            var loaded = YAML.readValue(manifestPath.toFile(), ManifestFile.class);
            return loaded.manifests() == null ? List.of() : loaded.manifests();
        } catch (IOException e) {
            // Lab guests may ship anchor aliases (e.g. *korus_bundled) that Jackson cannot bind; use runtime defaults.
            return List.of();
        }
    }

    public List<ConnectorProfile> profiles() {
        return List.of(
            supportedBundled("postgres-16-bundled", FAMILY_POSTGRES, CONNECTOR_POSTGRES_16, "pg"),
            supportedExternal("postgres-16-external", FAMILY_POSTGRES, CONNECTOR_POSTGRES_16, "pg"),
            supportedBundled("s3-minio-bundled", FAMILY_S3, "minio-s3", "s3"),
            supportedExternal("s3-compatible-external", FAMILY_S3, FAMILY_S3, "s3"),
            supportedBundled("nats-2.10-bundled", "nats", "nats-2.10", "nats"),
            supportedExternal("nats-2.x-external", "nats", "nats-2.x", "nats"),
            supportedBundled("keycloak-24-bundled", "oidc", "keycloak-24", "oidc"),
            supportedExternal("oidc-generic", "oidc", "oidc-generic", "oidc"),
            supportedBundled("redis-7-bundled", FAMILY_REDIS, "redis-7", PROTOCOL_REDIS),
            supportedExternal("redis-compatible-external", FAMILY_REDIS, FAMILY_REDIS, PROTOCOL_REDIS),
            supportedBundled("livekit-bundled", FAMILY_LIVEKIT, FAMILY_LIVEKIT, COMPONENT_MEDIA),
            integrationCandidate("vks-integration-candidate", FAMILY_LIVEKIT, "vks-integration", COMPONENT_MEDIA),
            supportedBundled("web-push-vapid", "web-push", "vapid", "notifications"),
            supportedExternal("dlp-external", "dlp-integration", PROTOCOL_HTTP_JSON, "dlp"),
            supportedBundled("connector-runtime-bundled", "connector-runtime", "webhook-plugin-bot-gateway", "integrations")
        );
    }

    private ManifestObservation observation(ComponentBackendManifest manifest) {
        var validation = ExternalStackManifestValidator.validateDesiredManifests(List.of(manifest));
        var probe = activeProbeService.probe(manifest);
        return new ManifestObservation(
            manifest,
            manifest,
            probe.healthy() ? "healthy" : "degraded",
            probe.degradedReason(),
            mergeProbeWarnings(manifest, validation, probe)
        );
    }

    private static ValidationResult mergeProbeWarnings(
        ComponentBackendManifest manifest,
        ValidationResult validation,
        ExternalStackProbeResult probe
    ) {
        if (probe.healthy() && probe.warnings().isEmpty() && probe.metadata().isEmpty()) {
            return validation;
        }
        var failures = new ArrayList<>(validation.failures());
        if (!probe.healthy()) {
            var reason = probe.degradedReason() != null && !probe.degradedReason().isBlank()
                ? probe.degradedReason()
                : "unhealthy";
            failures.add(manifest.component() + " probe failed: " + reason);
        }
        var warnings = new ArrayList<>(validation.warnings());
        probe.warnings().forEach(w -> warnings.add(manifest.component() + " probe warning: " + w));
        var metadata = new LinkedHashMap<>(validation.metadata());
        probe.metadata().forEach((k, v) -> metadata.put(manifest.component() + "." + k, v));
        return new ValidationResult(
            failures.isEmpty(),
            failures,
            warnings,
            validation.redacted(),
            metadata
        );
    }

    private ComponentBackendManifest manifest(ManifestSpec spec) {
        return new ComponentBackendManifest(
            spec.component(),
            spec.backendFamily(),
            spec.connector(),
            "configured",
            ExternalStackRole.ACTIVE,
            spec.endpoint(),
            spec.resourceNameOrAlias(),
            spec.schemaOrProtocolVersion(),
            spec.compatibilityProfile(),
            "configured",
            "app-config",
            spec.capabilities(),
            spec.dataClassification(),
            SupportBoundary.bundled("korus"),
            Map.of("serve_traffic", "true")
        );
    }

    private static ConnectorProfile supportedBundled(
        String profileId,
        String backendFamily,
        String connector,
        String validationContract
    ) {
        return new ConnectorProfile(
            profileId,
            backendFamily,
            connector,
            LifecycleStatus.SUPPORTED_BUNDLED,
            List.of(DeploymentMode.BUNDLED),
            List.of("runtime_manifest"),
            validationContract,
            SupportBoundary.bundled("korus"),
            new ImpactModel("bundled-default", "korus-managed", "bundled-sizing", "included-in-box", "korus-runbook")
        );
    }

    private static ConnectorProfile supportedExternal(
        String profileId,
        String backendFamily,
        String connector,
        String validationContract
    ) {
        return new ConnectorProfile(
            profileId,
            backendFamily,
            connector,
            LifecycleStatus.SUPPORTED_EXTERNAL_BYO,
            List.of(DeploymentMode.EXTERNAL_BYO, DeploymentMode.MANAGED_BY_CUSTOMER),
            List.of("runtime_manifest"),
            validationContract,
            SupportBoundary.externalByo("customer"),
            new ImpactModel("profile-dependent", "customer-ha", "customer-sized", "customer-tco", "customer-runbook")
        );
    }

    private static ConnectorProfile integrationCandidate(
        String profileId,
        String backendFamily,
        String connector,
        String validationContract
    ) {
        return new ConnectorProfile(
            profileId,
            backendFamily,
            connector,
            LifecycleStatus.INTEGRATION_CANDIDATE,
            List.of(DeploymentMode.EXTERNAL_BYO, DeploymentMode.RF_CANDIDATE),
            List.of("integration_spike_required"),
            validationContract,
            SupportBoundary.externalByo("vendor"),
            new ImpactModel(UNKNOWN, UNKNOWN, UNKNOWN, "customer-tco", "separate-integration-runbook")
        );
    }

    private record ManifestSpec(
        String component,
        String backendFamily,
        String connector,
        String endpoint,
        String resourceNameOrAlias,
        String schemaOrProtocolVersion,
        String compatibilityProfile,
        List<String> capabilities,
        String dataClassification
    ) {}

    private record ManifestFile(
        @JsonProperty("manifests") List<ComponentBackendManifest> manifests
    ) {}
}
