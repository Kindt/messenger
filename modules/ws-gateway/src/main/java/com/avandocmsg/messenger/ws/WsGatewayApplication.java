package com.avandocmsg.messenger.ws;

import com.avandocmsg.messenger.common.i18n.CompositeMessageSource;
import com.avandocmsg.messenger.ws.auth.WsTokenValidator;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.server.WsContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

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
        MessagingWebSocket.natsConnection = Nats.connect(natsOptions);
        log.info("NATS connected: {}", natsUrl);

        MessagingWebSocket.allowedOrigins = WsOriginPolicy.parseAllowedOrigins(
            System.getenv("WS_ALLOWED_ORIGINS"));

        var tomcat = new Tomcat();
        tomcat.setPort(port);
        var connector = tomcat.getConnector();
        connector.setProperty("bindOnInit", "false");

        var ctx = tomcat.addContext("", null);
        ctx.setParentClassLoader(WsGatewayApplication.class.getClassLoader());
        ctx.addApplicationListener(WsContextListener.class.getName());

        tomcat.start();

        var wsContainer = (ServerContainer) ctx.getServletContext()
            .getAttribute(ServerContainer.class.getName());
        wsContainer.addEndpoint(ServerEndpointConfig.Builder
            .create(MessagingWebSocket.class, "/ws")
            .build());

        log.info("ws-gateway started on port {}", port);
        tomcat.getServer().await();
    }
}
