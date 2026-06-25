package com.avandocmsg.messenger.ws;

import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;

class WsSessionRegistryTest {

    @Test
    void register_enforcesMaxPerUser() {
        var registry = new WsSessionRegistry(2, 100);
        var userId = "user-1";
        assertEquals(WsSessionRegistry.RegisterResult.ACCEPTED,
            registry.register(mock(Session.class), userId, List.of()));
        assertEquals(WsSessionRegistry.RegisterResult.ACCEPTED,
            registry.register(mock(Session.class), userId, List.of()));
        assertEquals(WsSessionRegistry.RegisterResult.MAX_PER_USER,
            registry.register(mock(Session.class), userId, List.of()));
    }

    @Test
    void register_tracksChatMembershipForBroadcast() {
        var registry = new WsSessionRegistry(5, 100);
        var userId = "user-1";
        var chatId = "chat-1";
        var session = mock(Session.class);
        assertEquals(WsSessionRegistry.RegisterResult.ACCEPTED,
            registry.register(session, userId, java.util.List.of(chatId)));
        assertTrue(registry.userIdsForChat(chatId).contains(userId));
        assertEquals(1, registry.sessionsForUser(userId).size());
    }

    @Test
    void unregister_removesChatMembershipWhenLastSessionClosed() {
        var registry = new WsSessionRegistry(5, 100);
        var userId = "user-1";
        var chatId = "chat-1";
        var session = mock(Session.class);
        registry.register(session, userId, java.util.List.of(chatId));
        registry.unregister(session);
        assertTrue(registry.userIdsForChat(chatId).isEmpty());
    }

    @Test
    void allOpenSessions_returnsRegisteredSessions() {
        var registry = new WsSessionRegistry(5, 100);
        var sessionA = mock(Session.class);
        var sessionB = mock(Session.class);
        registry.register(sessionA, "user-a", List.of());
        registry.register(sessionB, "user-b", List.of());
        assertEquals(2, registry.allOpenSessions().size());
        registry.unregister(sessionA);
        assertEquals(1, registry.allOpenSessions().size());
    }
}
