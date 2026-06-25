package com.avandocmsg.messenger.ws;

import com.avandocmsg.messenger.common.scheduling.ScheduledTaskSupport;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Protocol ping/pong + stale session eviction (spec 025 FR-091 / FR-092). */
public final class WsSessionKeepalive implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WsSessionKeepalive.class);
    private static final byte[] PING_PAYLOAD = new byte[] {0x4b, 0x57}; // "KW"

    private final WsSessionRegistry registry;
    private final Consumer<Session> onEvict;
    private final long pingIntervalMs;
    private final long pongTimeoutMs;
    private final ConcurrentMap<Session, Long> lastActivityMs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public WsSessionKeepalive(
            WsSessionRegistry registry,
            WsKeepaliveSettings settings,
            Consumer<Session> onEvict) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.onEvict = Objects.requireNonNull(onEvict, "onEvict");
        this.pingIntervalMs = Math.max(1_000L, settings.pingIntervalMs());
        this.pongTimeoutMs = Math.max(pingIntervalMs, settings.pongTimeoutMs());
        if (settings.enabled()) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(r, "ws-session-keepalive");
                t.setDaemon(true);
                return t;
            });
            long intervalMs = Math.max(1_000L, pingIntervalMs);
            ScheduledTaskSupport.scheduleAtFixedRateWithJitter(
                scheduler, this::tickSafe, intervalMs, intervalMs, intervalMs / 5, TimeUnit.MILLISECONDS);
        } else {
            this.scheduler = null;
        }
    }

    public void onRegistered(Session session) {
        if (session == null) {
            return;
        }
        lastActivityMs.put(session, System.currentTimeMillis());
    }

    /** Test hook: seed activity timestamp (same package tests). */
    void seedLastActivity(Session session, long activityMs) {
        if (session != null) {
            lastActivityMs.put(session, activityMs);
        }
    }

    public void onUnregistered(Session session) {
        if (session != null) {
            lastActivityMs.remove(session);
        }
    }

    public void touch(Session session) {
        if (session == null) {
            return;
        }
        lastActivityMs.computeIfPresent(session, (s, prev) -> System.currentTimeMillis());
    }

    public void onPong(Session session) {
        touch(session);
    }

    int evictStale(long nowMs) {
        int evicted = 0;
        for (var session : registry.allOpenSessions()) {
            var last = lastActivityMs.get(session);
            if (last == null || nowMs - last > pongTimeoutMs) {
                if (evictSession(session, "stale")) {
                    evicted++;
                }
            }
        }
        return evicted;
    }

    void pingOpenSessions() {
        for (var session : registry.allOpenSessions()) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.getAsyncRemote().sendPing(ByteBuffer.wrap(PING_PAYLOAD));
            } catch (Exception e) {
                log.debug("WS ping failed for session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    void tick(long nowMs) {
        evictStale(nowMs);
        pingOpenSessions();
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        lastActivityMs.clear();
    }

    private void tickSafe() {
        try {
            tick(System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("WS keepalive tick failed: {}", e.getMessage());
        }
    }

    private boolean evictSession(Session session, String reason) {
        lastActivityMs.remove(session);
        try {
            if (session.isOpen()) {
                session.close(new CloseReason(() -> 4002, reason));
            }
        } catch (Exception e) {
            log.debug("Failed to close stale WS session {}: {}", session.getId(), e.getMessage());
        }
        try {
            onEvict.accept(session);
        } catch (Exception e) {
            log.warn("WS keepalive eviction callback failed for session {}: {}", session.getId(), e.getMessage());
        }
        return true;
    }
}
