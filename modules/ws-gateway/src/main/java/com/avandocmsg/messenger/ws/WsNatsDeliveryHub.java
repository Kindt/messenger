package com.avandocmsg.messenger.ws;

import com.avandocmsg.messenger.common.nats.DeliverSubject;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/** Single NATS dispatcher for user + chat deliver subjects (PS-1.1 / PS-1.3). */
public final class WsNatsDeliveryHub implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WsNatsDeliveryHub.class);
    static final String DELIVER_WILDCARD = NatsSubjects.MSG_DELIVER_PREFIX + ">";

    private final WsSessionRegistry registry;
    private final Connection connection;
    private final Dispatcher dispatcher;
    private final AtomicReference<WsSessionKeepalive> keepalive = new AtomicReference<>();

    public WsNatsDeliveryHub(Connection connection, WsSessionRegistry registry) {
        this.connection = connection;
        this.registry = registry;
        this.dispatcher = connection.createDispatcher(this::onMessage);
        this.dispatcher.subscribe(DELIVER_WILDCARD);
        log.info("Subscribed to {} (shared dispatcher)", DELIVER_WILDCARD);
    }

    public void attachKeepalive(WsSessionKeepalive keepalive) {
        this.keepalive.set(keepalive);
    }

    public WsSessionRegistry.RegisterResult tryRegister(Session session, String userId, Collection<String> chatIds) {
        var result = registry.register(session, userId, chatIds);
        if (result == WsSessionRegistry.RegisterResult.ACCEPTED) {
            var ka = keepalive.get();
            if (ka != null) {
                ka.onRegistered(session);
            }
        }
        WsGatewayMetrics.setActiveSessions(registry.openSessionCount());
        return result;
    }

    public void unregister(Session session) {
        var ka = keepalive.get();
        if (ka != null) {
            ka.onUnregistered(session);
        }
        registry.unregister(session);
        WsGatewayMetrics.setActiveSessions(registry.openSessionCount());
    }

    public int openSessionCount() {
        return registry.openSessionCount();
    }

    void onMessage(Message msg) {
        if (msg == null) {
            return;
        }
        var target = DeliverSubject.parse(msg.getSubject());
        if (target == null) {
            return;
        }
        if (target.type() == DeliverSubject.Type.CHAT) {
            deliverToChat(target.id(), msg.getData());
        } else {
            deliverToUser(target.id(), msg.getData());
        }
    }

    private void deliverToUser(String userId, byte[] payload) {
        sendText(registry.sessionsForUser(userId), userId, payload);
    }

    private void deliverToChat(String chatId, byte[] payload) {
        for (var userId : registry.userIdsForChat(chatId)) {
            sendText(registry.sessionsForUser(userId), userId, payload);
        }
    }

    private void sendText(Collection<Session> sessions, String userId, byte[] payload) {
        WsGatewayMetrics.addDeliveredBytes(payload.length);
        WsGatewayMetrics.addFanoutRecipients(sessions.size());
        var text = new String(payload, StandardCharsets.UTF_8);
        for (Session session : sessions) {
            try {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(text);
                }
            } catch (Exception e) {
                log.warn("Failed to send WS message to user {} session {}", userId, session.getId(), e);
            }
        }
    }

    @Override
    public void close() {
        try {
            connection.closeDispatcher(dispatcher);
        } catch (Exception e) {
            log.debug("Failed to close NATS dispatcher: {}", e.getMessage());
        }
    }
}
