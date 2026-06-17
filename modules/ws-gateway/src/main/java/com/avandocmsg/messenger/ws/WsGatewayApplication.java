package com.avandocmsg.messenger.ws;

import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.CompositeMessageSource;
import com.avandocmsg.messenger.common.jdbc.HikariDataSources;
import com.avandocmsg.messenger.ws.auth.WsTokenValidator;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.apache.tomcat.websocket.server.WsSci;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WsGatewayApplication {
    private static final Logger log = LoggerFactory.getLogger(WsGatewayApplication.class);

    public static void main(String[] args) throws Exception {
        var port = Integer.parseInt(System.getenv().getOrDefault("WS_PORT", "8081"));
        var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        var keycloakIssuer = System.getenv().getOrDefault("KEYCLOAK_ISSUER",
            "http://localhost:8081/realms/avandocmsg");
        var keycloakJwksUrl = System.getenv().getOrDefault("KEYCLOAK_JWKS_URL",
            "http://localhost:8081/realms/avandocmsg/protocol/openid-connect/certs");

        MessagingWebSocket.tokenValidator = new WsTokenValidator(keycloakIssuer, keycloakJwksUrl);

        var localeRaw = System.getenv("APP_LOCALE");
        var localeTag = localeRaw == null || localeRaw.isBlank()
            ? "ru"
            : localeRaw.trim().replace('_', '-');
        MessagingWebSocket.messages = new CompositeMessageSource(
            Locale.forLanguageTag(localeTag),
            WsGatewayApplication.class.getClassLoader(),
            List.of("com.avandocmsg.messenger.i18n.messages_ws_gateway"));

        var natsOptions = Options.builder()
            .server(natsUrl)
            .connectionName("ws-gateway")
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(-1)
            .build();
        Connection natsConnection = Nats.connect(natsOptions);
        MessagingWebSocket.natsConnection = natsConnection;
        log.info("NATS connected: {}", natsUrl);

        var dbUrl = System.getenv("DB_JDBC_URL");
        var dbUser = System.getenv().getOrDefault("DB_USER", "avandocmsg");
        var dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "avandocmsg");
        var dataSource = HikariDataSources.createOptionalPool(dbUrl, dbUser, dbPassword, 3, "ws-gateway");
        if (dataSource != null) {
            MessagingWebSocket.chatMembershipLoader = new WsChatMembershipLoader(dataSource);
            log.info("WS chat membership loader enabled (JDBC)");
        } else {
            MessagingWebSocket.chatMembershipLoader = null;
            log.info("WS chat membership loader disabled (DB_JDBC_URL unset; large-chat broadcast needs JDBC)");
        }

        var limits = WsConnectionLimits.fromEnv();
        var sessionRegistry = new WsSessionRegistry(limits.maxPerUser(), limits.maxTotal());
        MessagingWebSocket.deliveryHub = new WsNatsDeliveryHub(natsConnection, sessionRegistry);
        WsGatewayMetrics.setOpenSessions(0);
        log.info("WS delivery hub ready (maxPerUser={}, maxTotal={})", limits.maxPerUser(), limits.maxTotal());

        WorkerHealthHttpServer metricsServer = null;
        var metricsPort = parsePort(System.getenv("WS_METRICS_PORT"), 9191);
        if (metricsPort > 0) {
            metricsServer = WorkerHealthHttpServer.startWithMetrics(
                metricsPort, "ws-gateway-metrics", () -> true, null, null);
            log.info("WS metrics on port {} (/metrics, /health)", metricsServer.getPort());
        }
        final WorkerHealthHttpServer metricsRef = metricsServer;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (metricsRef != null) {
                metricsRef.close();
            }
            if (MessagingWebSocket.deliveryHub != null) {
                MessagingWebSocket.deliveryHub.close();
            }
            try {
                natsConnection.close();
            } catch (Exception e) {
                log.debug("NATS close on shutdown: {}", e.getMessage());
            }
            HikariDataSources.closeQuietly(dataSource);
        }, "ws-gateway-shutdown"));

        MessagingWebSocket.allowedOrigins = WsOriginPolicy.parseAllowedOrigins(
            System.getenv("WS_ALLOWED_ORIGINS"));

        var tomcat = new Tomcat();
        tomcat.setPort(port);
        var connector = tomcat.getConnector();
        connector.setProperty("bindOnInit", "false");

        File docBase = Files.createTempDirectory("ws-gateway-docbase").toFile();
        docBase.deleteOnExit();
        var ctx = tomcat.addWebapp("", docBase.getAbsolutePath());
        ctx.setParentClassLoader(WsGatewayApplication.class.getClassLoader());
        if (ctx instanceof StandardContext standardContext) {
            var jarScanner = new StandardJarScanner();
            jarScanner.setScanClassPath(true);
            standardContext.setJarScanner(jarScanner);
        }
        ctx.addServletContainerInitializer(new WsSci(), Set.of(MessagingWebSocket.class));

        tomcat.start();

        log.info("ws-gateway started on port {} (/ws)", port);
        tomcat.getServer().await();
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
