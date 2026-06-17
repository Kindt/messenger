package com.avandocmsg.messenger.ws;

import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WsSessionRegistryTest {

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
}
