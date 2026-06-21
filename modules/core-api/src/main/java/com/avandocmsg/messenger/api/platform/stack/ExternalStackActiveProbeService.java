package com.avandocmsg.messenger.api.platform.stack;

import com.avandocmsg.messenger.api.config.AppConfig;

import javax.sql.DataSource;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class ExternalStackActiveProbeService {

    private static final ExternalStackActiveProbeService EMPTY = new ExternalStackActiveProbeService(Map.of());

    private final Map<String, ExternalStackActiveProbe> probes;

    private ExternalStackActiveProbeService(Map<String, ExternalStackActiveProbe> probes) {
        this.probes = Map.copyOf(probes);
    }

    public static ExternalStackActiveProbeService none() {
        return EMPTY;
    }

    public static ExternalStackActiveProbeService of(Map<String, ExternalStackActiveProbe> probes) {
        return probes == null || probes.isEmpty() ? EMPTY : new ExternalStackActiveProbeService(probes);
    }

    public static ExternalStackActiveProbeService bounded(
        AppConfig appConfig,
        DataSource dataSource,
        BooleanSupplier redisPing
    ) {
        return of(Map.of(
            "relational-db-hot", manifest -> jdbcMetadataProbe(dataSource),
            "cache", manifest -> redisProbe(redisPing),
            "idp", manifest -> oidcShapeProbe(appConfig.keycloakIssuer()),
            "web-edge", manifest -> urlShapeProbe(appConfig.webPublicBaseUrl(), "web-edge"),
            "object-storage", manifest -> configuredEndpointProbe(appConfig.minioEndpoint(), "object-storage", "s3 client not attached"),
            "messaging", manifest -> configuredEndpointProbe(appConfig.natsUrl(), "messaging", "nats client not attached")
        ));
    }

    public ExternalStackProbeResult probe(ComponentBackendManifest manifest) {
        var probe = probes.get(manifest.component());
        if (probe == null) {
            return ExternalStackProbeResult.ok();
        }
        try {
            return probe.probe(manifest);
        } catch (Exception e) {
            return ExternalStackProbeResult.degraded("probe failed: " + e.getMessage());
        }
    }

    private static ExternalStackProbeResult jdbcMetadataProbe(DataSource dataSource) {
        if (dataSource == null) {
            return ExternalStackProbeResult.degraded("jdbc datasource not attached");
        }
        try (var conn = dataSource.getConnection()) {
            var metadata = conn.getMetaData();
            var values = new HashMap<String, String>();
            values.put("database_product", metadata.getDatabaseProductName());
            values.put("database_version", metadata.getDatabaseProductVersion());
            return ExternalStackProbeResult.ok(values);
        } catch (Exception e) {
            return ExternalStackProbeResult.degraded("jdbc metadata probe failed: " + e.getMessage());
        }
    }

    private static ExternalStackProbeResult redisProbe(BooleanSupplier redisPing) {
        if (redisPing == null) {
            return ExternalStackProbeResult.degraded("redis probe not attached");
        }
        try {
            return redisPing.getAsBoolean()
                ? ExternalStackProbeResult.ok()
                : ExternalStackProbeResult.degraded("redis ping failed");
        } catch (Exception e) {
            return ExternalStackProbeResult.degraded("redis ping failed: " + e.getMessage());
        }
    }

    private static ExternalStackProbeResult oidcShapeProbe(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return ExternalStackProbeResult.degraded("oidc issuer not configured");
        }
        var uri = parseUri(issuer, "oidc issuer");
        if (uri == null) {
            return ExternalStackProbeResult.degraded("oidc issuer is not a valid URI");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return ExternalStackProbeResult.degraded("oidc issuer must use https");
        }
        var path = uri.getPath() == null ? "" : uri.getPath();
        if (!path.contains("/realms/")) {
            return ExternalStackProbeResult.ok(Map.of(), "oidc issuer does not include /realms/ path");
        }
        return ExternalStackProbeResult.ok();
    }

    private static ExternalStackProbeResult urlShapeProbe(String url, String label) {
        if (url == null || url.isBlank()) {
            return ExternalStackProbeResult.degraded(label + " url not configured");
        }
        var uri = parseUri(url, label + " url");
        if (uri == null) {
            return ExternalStackProbeResult.degraded(label + " url is not a valid URI");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return ExternalStackProbeResult.degraded(label + " url must use https", "security_headers_not_verified");
        }
        return ExternalStackProbeResult.ok(Map.of(), "security_headers_placeholder");
    }

    private static ExternalStackProbeResult configuredEndpointProbe(String endpoint, String component, String warning) {
        if (endpoint == null || endpoint.isBlank() || "not-configured".equals(endpoint)) {
            return ExternalStackProbeResult.degraded(component + " endpoint not configured");
        }
        return ExternalStackProbeResult.ok(Map.of(), warning + "; configured-only probe");
    }

    private static URI parseUri(String value, String label) {
        try {
            return URI.create(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
