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
            observation(manifest(
                "relational-db-hot",
                "postgres",
                "postgres-16",
                appConfig.dbJdbcUrl(),
                "avandocmsg_hot",
                "flyway-current",
                "postgres-16-bundled",
                List.of("jdbc_connectivity", "flyway_privileges", "pool_sizing"),
                "hot-personal-data"
            )),
            observation(manifest(
                "object-storage",
                "s3-compatible",
                "minio-s3",
                appConfig.minioEndpoint(),
                appConfig.minioBucket(),
                "s3",
                "s3-minio-bundled",
                List.of("put_get_head_delete_list", "multipart", "checksum"),
                "file-content"
            )),
            observation(manifest(
                "messaging",
                "nats",
                "nats-2.10",
                appConfig.natsUrl(),
                appConfig.natsJetstream() ? "jetstream" : "core-nats",
                appConfig.natsJetstream() ? "jetstream" : "core",
                "nats-2.10-bundled",
                List.of("publish_subscribe_subject_prefixes", "queue_groups", "drain_behavior"),
                "event-metadata"
            )),
            observation(manifest(
                "idp",
                "oidc",
                "keycloak-24",
                appConfig.keycloakIssuer(),
                "avandocmsg",
                "oidc",
                "keycloak-24-bundled",
                List.of("issuer_jwks_tls", "token_signature_audience_issuer", "required_claims_user_org_roles"),
                "identity-security"
            )),
            observation(manifest(
                "cache",
                "redis-compatible",
                "redis-7",
                appConfig.redisUri(),
                "default",
                "redis",
                "redis-7-bundled",
                List.of("command_subset_get_set_del_expire_counters_ttl", "key_prefix_isolation"),
                "cache-security-adjacent"
            )),
            observation(manifest(
                "web-edge",
                "http-reverse-proxy",
                "tomcat-11",
                appConfig.webPublicBaseUrl(),
                "public-web",
                "http",
                "nginx-bundled",
                List.of("health_routing", "forwarded_headers", "security_headers"),
                "public-edge"
            )),
            observation(manifest(
                "media",
                "livekit",
                "livekit",
                appConfig.livekitUrl(),
                appConfig.livekitUrl().isBlank() ? "mesh-webrtc" : "livekit",
                "livekit",
                "livekit-1.8-bundled",
                List.of("livekit_token_issue", "room_join", "vks_integration_candidate_gate"),
                "media-metadata"
            )),
            observation(manifest(
                "turn",
                "stun-turn",
                "webrtc-ice",
                appConfig.webrtcStunUris(),
                "ice",
                "webrtc",
                "explicit",
                List.of("realm_secret", "relay_reachability", "udp_tcp_ports"),
                "media-network"
            )),
            observation(manifest(
                "notifications",
                "web-push",
                "vapid",
                appConfig.webClientVapidPublicKey().orElse("not-configured"),
                "browser-push",
                "webpush",
                "webpush-vapid-bundled-config",
                List.of("vapid_config", "gateway_config", "best_effort_semantics"),
                "notification-metadata"
            )),
            observation(manifest(
                "dlp",
                "dlp-integration",
                "connector-runtime-dlp",
                appConfig.integrationsBaseUrl(),
                "dlp-policy",
                "http-json",
                "explicit",
                List.of("endpoint_auth_tls", "verdict_schema", "tenant_policy", "e2ee_plaintext_boundary"),
                "compliance-boundary"
            )),
            observation(manifest(
                "integrations",
                "connector-runtime",
                "webhook-plugin-bot-gateway",
                appConfig.integrationsBaseUrl(),
                "integrations-gateway",
                "http-json",
                "http-webhook-generic",
                List.of("webhook_plugin_bot_gateway_endpoint", "event_schema", "retry_timeout", "audit_boundary"),
                "integration-events"
            ))
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
            throw new IllegalStateException("Cannot read external stack manifest: " + manifestPath, e);
        }
    }

    public List<ConnectorProfile> profiles() {
        return List.of(
            supportedBundled("postgres-16-bundled", "postgres", "postgres-16", "pg"),
            supportedExternal("postgres-16-external", "postgres", "postgres-16", "pg"),
            supportedBundled("s3-minio-bundled", "s3-compatible", "minio-s3", "s3"),
            supportedExternal("s3-compatible-external", "s3-compatible", "s3-compatible", "s3"),
            supportedBundled("nats-2.10-bundled", "nats", "nats-2.10", "nats"),
            supportedExternal("nats-2.x-external", "nats", "nats-2.x", "nats"),
            supportedBundled("keycloak-24-bundled", "oidc", "keycloak-24", "oidc"),
            supportedExternal("oidc-generic", "oidc", "oidc-generic", "oidc"),
            supportedBundled("redis-7-bundled", "redis-compatible", "redis-7", "redis"),
            supportedExternal("redis-compatible-external", "redis-compatible", "redis-compatible", "redis"),
            supportedBundled("livekit-bundled", "livekit", "livekit", "media"),
            integrationCandidate("vks-integration-candidate", "livekit", "vks-integration", "media"),
            supportedBundled("web-push-vapid", "web-push", "vapid", "notifications"),
            supportedExternal("dlp-external", "dlp-integration", "http-json", "dlp"),
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
        if (probe.warnings().isEmpty() && probe.metadata().isEmpty()) {
            return validation;
        }
        var warnings = new ArrayList<>(validation.warnings());
        probe.warnings().forEach(w -> warnings.add(manifest.component() + " probe warning: " + w));
        var metadata = new LinkedHashMap<>(validation.metadata());
        probe.metadata().forEach((k, v) -> metadata.put(manifest.component() + "." + k, v));
        return new ValidationResult(
            validation.passed(),
            validation.failures(),
            warnings,
            validation.redacted(),
            metadata
        );
    }

    private ComponentBackendManifest manifest(
        String component,
        String backendFamily,
        String connector,
        String endpoint,
        String resourceNameOrAlias,
        String schemaOrProtocolVersion,
        String compatibilityProfile,
        List<String> capabilities,
        String dataClassification
    ) {
        return new ComponentBackendManifest(
            component,
            backendFamily,
            connector,
            "configured",
            ExternalStackRole.active,
            endpoint,
            resourceNameOrAlias,
            schemaOrProtocolVersion,
            compatibilityProfile,
            "configured",
            "app-config",
            capabilities,
            dataClassification,
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
            LifecycleStatus.supported_bundled,
            List.of(DeploymentMode.bundled),
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
            LifecycleStatus.supported_external_byo,
            List.of(DeploymentMode.external_byo, DeploymentMode.managed_by_customer),
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
            LifecycleStatus.integration_candidate,
            List.of(DeploymentMode.external_byo, DeploymentMode.rf_candidate),
            List.of("integration_spike_required"),
            validationContract,
            SupportBoundary.externalByo("vendor"),
            new ImpactModel("unknown", "unknown", "unknown", "customer-tco", "separate-integration-runbook")
        );
    }

    private record ManifestFile(
        @JsonProperty("manifests") List<ComponentBackendManifest> manifests
    ) {}
}
