package com.avandocmsg.messenger.ws;

import com.avandocmsg.messenger.common.dto.RtcSignalEvent;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.ws.auth.WsTokenValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/ws")
public class MessagingWebSocket {
    private static final Logger log = LoggerFactory.getLogger(MessagingWebSocket.class);
    private static final ObjectMapper WS_JSON = new ObjectMapper();

    static Connection natsConnection;
    static WsTokenValidator tokenValidator;
    /** Set in {@link WsGatewayApplication#main} before accepting connections. */
    static UserMessageSource messages;

    private final Map<Session, String> sessionUsers = new ConcurrentHashMap<>();
    private final Map<Session, Dispatcher> sessionDispatchers = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
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
        sessionUsers.put(session, userId);

        var dispatcher = natsConnection.createDispatcher(msg -> {
            try {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(new String(msg.getData(), StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                log.warn("Failed to send WS message to user {}", userId, e);
            }
        });
        dispatcher.subscribe(NatsSubjects.MSG_DELIVER_PREFIX + userId);
        sessionDispatchers.put(session, dispatcher);
        log.info("WS opened for user {} (session {})", userId, session.getId());
    }

    private static final int MAX_WS_TEXT_BYTES = 48_000;

    @OnMessage(maxMessageSize = 65_536)
    public void onMessage(String text, Session session) {
        var userId = sessionUsers.get(session);
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
        var dispatcher = sessionDispatchers.remove(session);
        if (dispatcher != null) natsConnection.closeDispatcher(dispatcher);
        var userId = sessionUsers.remove(session);
        if (userId != null) log.info("WS closed for user {} (session {})", userId, session.getId());
    }

    private String parseToken(String query) {
        if (query == null) return null;
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
