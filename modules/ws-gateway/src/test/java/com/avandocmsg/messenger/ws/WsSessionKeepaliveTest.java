package com.avandocmsg.messenger.ws;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WsSessionKeepaliveTest {

    @Test
    void evictStale_closesAndUnregistersIdleSessions() throws Exception {
        var registry = new WsSessionRegistry(5, 100);
        var evicted = new ArrayList<Session>();
        var keepalive = new WsSessionKeepalive(
            registry,
            new WsKeepaliveSettings(true, 1_000, 2_000),
            s -> {
                evicted.add(s);
                registry.unregister(s);
            });

        var session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        registry.register(session, "user-1", List.of());
        keepalive.onRegistered(session);
        keepalive.seedLastActivity(session, 1_000L);

        assertEquals(1, keepalive.evictStale(4_000L));
        verify(session).close(org.mockito.ArgumentMatchers.any());
        assertEquals(1, evicted.size());
        assertTrue(registry.allOpenSessions().isEmpty());
        keepalive.close();
    }

    @Test
    void onPong_refreshesActivity_andSkipsEviction() {
        var registry = new WsSessionRegistry(5, 100);
        var evicted = new AtomicInteger();
        var keepalive = new WsSessionKeepalive(
            registry,
            new WsKeepaliveSettings(true, 1_000, 5_000),
            s -> evicted.incrementAndGet());

        var session = mock(Session.class);
        registry.register(session, "user-1", List.of());
        keepalive.onRegistered(session);
        keepalive.onPong(session);

        assertEquals(0, keepalive.evictStale(4_000L));
        assertEquals(0, evicted.get());
        assertEquals(1, registry.allOpenSessions().size());
        keepalive.close();
    }

    @Test
    void pingOpenSessions_sendsPingToOpenSessions() throws Exception {
        var registry = new WsSessionRegistry(5, 100);
        var keepalive = new WsSessionKeepalive(
            registry,
            new WsKeepaliveSettings(true, 1_000, 5_000),
            s -> {});

        var session = mock(Session.class);
        var async = mock(RemoteEndpoint.Async.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getAsyncRemote()).thenReturn(async);
        registry.register(session, "user-1", List.of());
        keepalive.onRegistered(session);

        keepalive.pingOpenSessions();

        verify(async).sendPing(org.mockito.ArgumentMatchers.any());
        keepalive.close();
    }

    @Test
    void tick_evictsStaleThenPingsRemainingSessions() throws Exception {
        var registry = new WsSessionRegistry(5, 100);
        var evicted = new AtomicInteger();
        var keepalive = new WsSessionKeepalive(
            registry,
            new WsKeepaliveSettings(true, 1_000, 2_000),
            s -> {
                evicted.incrementAndGet();
                registry.unregister(s);
            });

        var stale = mock(Session.class);
        var alive = mock(Session.class);
        var async = mock(RemoteEndpoint.Async.class);
        when(alive.isOpen()).thenReturn(true);
        when(alive.getAsyncRemote()).thenReturn(async);
        registry.register(stale, "user-stale", List.of());
        registry.register(alive, "user-alive", List.of());
        keepalive.onRegistered(stale);
        keepalive.onRegistered(alive);
        keepalive.seedLastActivity(stale, 1_000L);
        keepalive.seedLastActivity(alive, 3_500L);

        keepalive.tick(4_000L);

        assertEquals(1, evicted.get());
        assertEquals(1, registry.allOpenSessions().size());
        verify(async).sendPing(org.mockito.ArgumentMatchers.any());
        keepalive.close();
    }
}
