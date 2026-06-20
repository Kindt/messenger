package com.avandocmsg.messenger.ws.bootstrap;

import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.CompositeMessageSource;
import com.avandocmsg.messenger.common.jdbc.HikariDataSources;
import com.avandocmsg.messenger.ws.MessagingWebSocket;
import com.avandocmsg.messenger.ws.WsChatMembershipLoader;
import com.avandocmsg.messenger.ws.WsConnectionLimits;
import com.avandocmsg.messenger.ws.WsGatewayMetrics;
import com.avandocmsg.messenger.ws.WsNatsDeliveryHub;
import com.avandocmsg.messenger.ws.WsOriginPolicy;
import com.avandocmsg.messenger.ws.WsSessionRegistry;
import com.avandocmsg.messenger.ws.auth.WsTokenValidator;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** Shared composition root for embedded and WAR deployment (spec 021 Phase 7.3). */
public final class WsGatewayComposition implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WsGatewayComposition.class);

    private final Connection natsConnection;
    private final DataSource dataSource;
    private final WorkerHealthHttpServer metricsServer;
    private final WsNatsDeliveryHub deliveryHub;

    private WsGatewayComposition(
            Connection natsConnection,
            DataSource dataSource,
            WorkerHealthHttpServer metricsServer,
            WsNatsDeliveryHub deliveryHub) {
        this.natsConnection = natsConnection;
        this.dataSource = dataSource;
        this.metricsServer = metricsServer;
        this.deliveryHub = deliveryHub;
    }

    public static WsGatewayComposition start() throws Exception {
        var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        var keycloakIssuer = System.getenv().getOrDefault("KEYCLOAK_ISSUER",
            "http://localhost:8081/realms/avandocmsg");
        var keycloakJwksUrl = System.getenv().getOrDefault("KEYCLOAK_JWKS_URL",
            "http://localhost:8081/realms/avandocmsg/protocol/openid-connect/certs");

        var localeRaw = System.getenv("APP_LOCALE");
        var localeTag = localeRaw == null || localeRaw.isBlank()
            ? "ru"
            : localeRaw.trim().replace('_', '-');

        var natsOptions = Options.builder()
            .server(natsUrl)
            .connectionName("ws-gateway")
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(-1)
            .build();
        Connection natsConnection = Nats.connect(natsOptions);
        log.info("NATS connected: {}", natsUrl);

        var dbUrl = System.getenv("DB_JDBC_URL");
        var dbUser = System.getenv().getOrDefault("DB_USER", "avandocmsg");
        var dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "avandocmsg");
        var dataSource = HikariDataSources.createOptionalPool(dbUrl, dbUser, dbPassword, 3, "ws-gateway");

        var limits = WsConnectionLimits.fromEnv();
        var sessionRegistry = new WsSessionRegistry(limits.maxPerUser(), limits.maxTotal());
        var deliveryHub = new WsNatsDeliveryHub(natsConnection, sessionRegistry);
        WsGatewayMetrics.setOpenSessions(0);
        log.info("WS delivery hub ready (maxPerUser={}, maxTotal={})", limits.maxPerUser(), limits.maxTotal());

        WorkerHealthHttpServer metricsServer = null;
        var metricsPort = parsePort(System.getenv("WS_METRICS_PORT"), 9191);
        if (metricsPort > 0) {
            metricsServer = WorkerHealthHttpServer.startWithMetrics(
                metricsPort, "ws-gateway-metrics", () -> true, null, null);
            log.info("WS metrics on port {} (/metrics, /health)", metricsServer.getPort());
        }

        var allowedOrigins = WsOriginPolicy.parseAllowedOrigins(System.getenv("WS_ALLOWED_ORIGINS"));
        MessagingWebSocket.configureStaticContext(
            new WsTokenValidator(keycloakIssuer, keycloakJwksUrl),
            new CompositeMessageSource(
                Locale.forLanguageTag(localeTag),
                WsGatewayComposition.class.getClassLoader(),
                List.of("com.avandocmsg.messenger.i18n.messages_ws_gateway")),
            natsConnection,
            dataSource != null ? new WsChatMembershipLoader(dataSource) : null,
            deliveryHub,
            allowedOrigins);

        if (dataSource != null) {
            log.info("WS chat membership loader enabled (JDBC)");
        } else {
            log.info("WS chat membership loader disabled (DB_JDBC_URL unset; large-chat broadcast needs JDBC)");
        }

        return new WsGatewayComposition(natsConnection, dataSource, metricsServer, deliveryHub);
    }

    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::closeQuietly, "ws-gateway-shutdown"));
    }

    @Override
    public void close() {
        if (metricsServer != null) {
            metricsServer.close();
        }
        if (deliveryHub != null) {
            deliveryHub.close();
        }
        try {
            natsConnection.close();
        } catch (Exception e) {
            log.debug("NATS close on shutdown: {}", e.getMessage());
        }
        HikariDataSources.closeQuietly(dataSource);
    }

    private void closeQuietly() {
        try {
            close();
        } catch (Exception e) {
            log.warn("WS gateway shutdown error: {}", e.getMessage());
        }
    }

    private static int parsePort(String raw, int defaultPort) {
        if (raw == null || raw.isBlank()) {
            return defaultPort;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }
}
