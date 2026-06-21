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
        return bounded(appConfig, dataSource, new ExternalStackProbeClients(
            redisPing, null, null, null, null, null, null, null
        ));
    }

    public static ExternalStackActiveProbeService bounded(
        AppConfig appConfig,
        DataSource dataSource,
        BooleanSupplier redisPing,
        BooleanSupplier s3Ping,
        BooleanSupplier natsPing
    ) {
        return bounded(appConfig, dataSource, new ExternalStackProbeClients(
            redisPing, null, s3Ping, null, natsPing, null, null, null
        ));
    }

    public static ExternalStackActiveProbeService bounded(
        AppConfig appConfig,
        DataSource dataSource,
        ExternalStackProbeClients clients
    ) {
        return of(Map.of(
            "relational-db-hot", manifest -> jdbcMetadataProbe(dataSource),
            "cache", manifest -> redisProbe(clients),
            "idp", manifest -> oidcProbe(appConfig.keycloakIssuer(), clients != null ? clients.oidcJwksReachable() : null),
            "web-edge", manifest -> webEdgeProbe(
                appConfig.webPublicBaseUrl(),
                clients != null ? clients.webEdgeSecurityHeaders() : null
            ),
            "object-storage", manifest -> s3Probe(
                appConfig.minioEndpoint(),
                clients != null ? clients.s3BucketExists() : null,
                clients != null ? clients.s3SampleOperation() : null
            ),
            "messaging", manifest -> natsProbe(
                appConfig.natsUrl(),
                clients != null ? clients.natsConnected() : null,
                clients != null ? clients.natsSubjectProbe() : null
            )
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

    private static ExternalStackProbeResult redisProbe(ExternalStackProbeClients clients) {
        var redisPing = clients != null ? clients.redisPing() : null;
        if (redisPing == null) {
            return ExternalStackProbeResult.degraded("redis probe not attached");
        }
        var ping = booleanProbe(redisPing, "redis ping ok", "redis ping failed");
        if (!ping.healthy()) {
            return ping;
        }
        var commandSubset = clients.redisCommandSubset();
        if (commandSubset == null) {
            return ping;
        }
        return booleanProbe(commandSubset, "redis command subset ok", "redis command subset probe failed");
    }

    private static ExternalStackProbeResult booleanProbe(
        BooleanSupplier probe,
        String successWarning,
        String failureReason
    ) {
        try {
            return probe.getAsBoolean()
                ? ExternalStackProbeResult.ok(Map.of(), successWarning)
                : ExternalStackProbeResult.degraded(failureReason);
        } catch (Exception e) {
            return ExternalStackProbeResult.degraded(failureReason + ": " + e.getMessage());
        }
    }

    private static ExternalStackProbeResult oidcProbe(String issuer, BooleanSupplier jwksReachable) {
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
        if (jwksReachable == null) {
            return ExternalStackProbeResult.ok();
        }
        return booleanProbe(jwksReachable, "oidc jwks reachable", "oidc jwks probe failed");
    }

    private static ExternalStackProbeResult webEdgeProbe(String url, BooleanSupplier securityHeaders) {
        var shape = urlShapeProbe(url, "web-edge");
        if (!shape.healthy() || securityHeaders == null) {
            return shape;
        }
        return booleanProbe(securityHeaders, "web-edge security headers ok", "web-edge security headers probe failed");
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

    private static ExternalStackProbeResult s3Probe(
        String endpoint,
        BooleanSupplier bucketExists,
        BooleanSupplier sampleOperation
    ) {
        if (bucketExists == null) {
            return configuredEndpointProbe(endpoint, "object-storage", "s3 client not attached");
        }
        var bucket = booleanProbe(bucketExists, "s3 bucket reachable", "s3 bucket probe failed");
        if (!bucket.healthy() || sampleOperation == null) {
            return bucket;
        }
        return booleanProbe(sampleOperation, "s3 sample operation ok", "s3 sample operation probe failed");
    }

    private static ExternalStackProbeResult natsProbe(
        String endpoint,
        BooleanSupplier connected,
        BooleanSupplier subjectProbe
    ) {
        if (connected == null) {
            return configuredEndpointProbe(endpoint, "messaging", "nats client not attached");
        }
        var connection = booleanProbe(connected, "nats client connected", "nats client disconnected");
        if (!connection.healthy() || subjectProbe == null) {
            return connection;
        }
        return booleanProbe(subjectProbe, "nats subject probe ok", "nats subject probe failed");
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
