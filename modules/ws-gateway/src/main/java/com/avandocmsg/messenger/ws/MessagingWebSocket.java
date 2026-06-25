package com.avandocmsg.messenger.ws;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.dto.RtcSignalEvent;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.ws.auth.WsTokenValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@ServerEndpoint(value = "/ws", configurator = MessagingWebSocket.OriginHandshakeConfigurator.class)
public class MessagingWebSocket {
    private static final Logger log = LoggerFactory.getLogger(MessagingWebSocket.class);
    private static final ObjectMapper WS_JSON = MessengerJson.mapper();
    static final String ORIGIN_PROP = "ws.origin";

    static Connection natsConnection;
    static WsNatsDeliveryHub deliveryHub;
    static WsSessionKeepalive sessionKeepalive;
    static WsChatMembershipLoader chatMembershipLoader;
    static WsTokenValidator tokenValidator;
    /** Set in {@link com.avandocmsg.messenger.ws.bootstrap.WsGatewayComposition#start()} before accepting connections. */
    static UserMessageSource messages;
    static List<String> allowedOrigins = List.of("*");
    static boolean perMessageDeflateEnabled = true;

    /** Called from {@link com.avandocmsg.messenger.ws.bootstrap.WsGatewayComposition} before accepting connections. */
    public static void configureStaticContext(
            WsTokenValidator validator,
            UserMessageSource messageSource,
            Connection nats,
            WsChatMembershipLoader membershipLoader,
            WsNatsDeliveryHub hub,
            WsSessionKeepalive keepalive,
            List<String> origins,
            boolean perMessageDeflate) {
        tokenValidator = validator;
        messages = messageSource;
        natsConnection = nats;
        chatMembershipLoader = membershipLoader;
        deliveryHub = hub;
        sessionKeepalive = keepalive;
        allowedOrigins = origins;
        perMessageDeflateEnabled = perMessageDeflate;
    }

    public static final class OriginHandshakeConfigurator extends ServerEndpointConfig.Configurator {
        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, jakarta.websocket.HandshakeResponse response) {
            var origins = request.getHeaders().get("Origin");
            if (origins != null && !origins.isEmpty()) {
                sec.getUserProperties().put(ORIGIN_PROP, origins.get(0));
            }
        }

        @Override
        public List<jakarta.websocket.Extension> getNegotiatedExtensions(
                List<jakarta.websocket.Extension> installed,
                List<jakarta.websocket.Extension> requested) {
            var negotiated = super.getNegotiatedExtensions(installed, requested);
            if (perMessageDeflateEnabled) {
                return negotiated;
            }
            return negotiated.stream()
                .filter(ext -> !"permessage-deflate".equalsIgnoreCase(ext.getName()))
                .toList();
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        var origin = (String) session.getUserProperties().get(ORIGIN_PROP);
        if (!WsOriginPolicy.isAllowed(origin, allowedOrigins)) {
            try {
                session.close(new CloseReason(() -> 4001, "origin denied"));
            } catch (Exception e) {
                log.warn("Failed to close WS for bad origin: {}", e.getMessage());
            }
            return;
        }
        var query = session.getQueryString();
        var token = parseToken(query);
        if (token == null) {
            closeWithError(session, messages.get("error.ws.missing_token"));
            return;
        }
        var claims = tokenValidator.validate(token);
        if (claims == null) {
            closeWithError(session, messages.get("error.ws.invalid_token"));
            return;
        }
        var userId = claims.getSubject();
        if (userId == null) {
            closeWithError(session, messages.get("error.ws.missing_sub"));
            return;
        }
        if (deliveryHub == null) {
            closeWithError(session, messages.get("error.ws.unavailable"));
            return;
        }
        List<String> chatIds = List.of();
        if (chatMembershipLoader != null) {
            try {
                chatIds = chatMembershipLoader.loadChatIds(UUID.fromString(userId));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid user id in token sub: {}", userId);
            }
        }
        var registerResult = deliveryHub.tryRegister(session, userId, chatIds);
        if (registerResult == WsSessionRegistry.RegisterResult.MAX_PER_USER) {
            closeWithError(session, messages.get("error.ws.max_connections_per_user"));
            return;
        }
        if (registerResult == WsSessionRegistry.RegisterResult.MAX_TOTAL) {
            closeWithError(session, messages.get("error.ws.max_connections_total"));
            return;
        }
        session.getUserProperties().put("ws.userId", userId);
        log.info("WS opened for user {} (session {}, chats={})", userId, session.getId(), chatIds.size());
    }

    private static final int MAX_WS_TEXT_BYTES = 48_000;

    @OnMessage(maxMessageSize = 65_536)
    public void onMessage(String text, Session session) {
        var ka = sessionKeepalive;
        if (ka != null) {
            ka.touch(session);
        }
        var userId = (String) session.getUserProperties().get("ws.userId");
        if (userId == null || natsConnection == null) {
            return;
        }
        if (text == null || text.length() > MAX_WS_TEXT_BYTES) {
            return;
        }
        try {
            JsonNode root = WS_JSON.readTree(text);
            if (!root.hasNonNull("type") || !RtcSignalEvent.TYPE.equals(root.get("type").asText())) {
                return;
            }
            if (!root.hasNonNull("chatId") || !root.has("payload") || !root.get("payload").isObject()) {
                return;
            }
            UUID.fromString(root.get("chatId").asText());
            JsonNode payload = root.get("payload");
            var evt = new RtcSignalEvent(root.get("chatId").asText(), userId, payload);
            natsConnection.publish(NatsSubjects.RTC_SIGNAL, WS_JSON.writeValueAsBytes(evt));
        } catch (Exception e) {
            log.debug("Ignoring invalid client WS payload: {}", e.toString());
        }
    }

    @OnMessage
    public void onPong(PongMessage pong, Session session) {
        var ka = sessionKeepalive;
        if (ka != null) {
            ka.onPong(session);
        }
    }

    @OnClose
    public void onClose(Session session) {
        cleanup(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.warn("WS error for session {}: {}", session.getId(), error.getMessage());
        cleanup(session);
    }

    private void cleanup(Session session) {
        if (deliveryHub != null) {
            deliveryHub.unregister(session);
        }
        var userId = session.getUserProperties().remove("ws.userId");
        if (userId != null) {
            log.info("WS closed for user {} (session {})", userId, session.getId());
        }
    }

    private String parseToken(String query) {
        if (query == null) {
            return null;
        }
        for (var param : query.split("&")) {
            var parts = param.split("=", 2);
            if (parts.length == 2 && "token".equals(parts[0])) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void closeWithError(Session session, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, reason));
        } catch (Exception e) {
            log.warn("Failed to close session: {}", e.getMessage());
        }
    }
}
