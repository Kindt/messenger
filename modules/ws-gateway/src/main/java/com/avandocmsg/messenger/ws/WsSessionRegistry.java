package com.avandocmsg.messenger.ws;

import jakarta.websocket.Session;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Maps user ids and chat memberships to open WebSocket sessions (PS-1.1 / PS-1.3). */
public final class WsSessionRegistry {

    public enum RegisterResult {
        ACCEPTED,
        MAX_PER_USER,
        MAX_TOTAL
    }

    private final int maxPerUser;
    private final int maxTotal;
    private final ConcurrentMap<String, Set<Session>> byUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<Session, String> sessionToUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> chatToUsers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> sessionCountByUser = new ConcurrentHashMap<>();
    private final AtomicInteger totalSessions = new AtomicInteger();

    public WsSessionRegistry(int maxPerUser, int maxTotal) {
        this.maxPerUser = Math.max(1, maxPerUser);
        this.maxTotal = Math.max(1, maxTotal);
    }

    public RegisterResult register(Session session, String userId, Collection<String> chatIds) {
        Objects.requireNonNull(session, "session");
        if (userId == null || userId.isBlank()) {
            return RegisterResult.MAX_PER_USER;
        }
        if (totalSessions.get() >= maxTotal) {
            return RegisterResult.MAX_TOTAL;
        }
        var count = sessionCountByUser.computeIfAbsent(userId, id -> new AtomicInteger(0));
        while (true) {
            var current = count.get();
            if (current >= maxPerUser) {
                return RegisterResult.MAX_PER_USER;
            }
            if (count.compareAndSet(current, current + 1)) {
                break;
            }
        }
        if (totalSessions.incrementAndGet() > maxTotal) {
            count.decrementAndGet();
            totalSessions.decrementAndGet();
            return RegisterResult.MAX_TOTAL;
        }
        var sessions = byUser.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet());
        if (!sessions.add(session)) {
            count.decrementAndGet();
            totalSessions.decrementAndGet();
            return RegisterResult.ACCEPTED;
        }
        sessionToUser.put(session, userId);
        linkUserToChats(userId, chatIds);
        return RegisterResult.ACCEPTED;
    }

    public void unregister(Session session) {
        if (session == null) {
            return;
        }
        var userId = sessionToUser.remove(session);
        if (userId == null) {
            return;
        }
        var sessions = byUser.get(userId);
        if (sessions != null && sessions.remove(session)) {
            totalSessions.updateAndGet(n -> Math.max(0, n - 1));
            var count = sessionCountByUser.get(userId);
            if (count != null) {
                count.updateAndGet(n -> Math.max(0, n - 1));
            }
            if (sessions.isEmpty()) {
                byUser.remove(userId, sessions);
                sessionCountByUser.remove(userId, count);
                unlinkUserFromAllChats(userId);
            }
        } else {
            totalSessions.updateAndGet(n -> Math.max(0, n - 1));
        }
    }

    public Collection<Session> sessionsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        var sessions = byUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        return List.copyOf(sessions);
    }

    public Collection<String> userIdsForChat(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return List.of();
        }
        var users = chatToUsers.get(chatId);
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        return List.copyOf(users);
    }

    public int openSessionCount() {
        return totalSessions.get();
    }

    /** Snapshot of all registered sessions (keepalive ping/eviction). */
    public Collection<Session> allOpenSessions() {
        if (sessionToUser.isEmpty()) {
            return List.of();
        }
        return List.copyOf(sessionToUser.keySet());
    }

    private void linkUserToChats(String userId, Collection<String> chatIds) {
        if (chatIds == null || chatIds.isEmpty()) {
            return;
        }
        for (var chatId : chatIds) {
            if (chatId == null || chatId.isBlank()) {
                continue;
            }
            chatToUsers.computeIfAbsent(chatId, id -> ConcurrentHashMap.newKeySet()).add(userId);
        }
    }

    private void unlinkUserFromAllChats(String userId) {
        for (var entry : chatToUsers.entrySet()) {
            var users = entry.getValue();
            if (users.remove(userId) && users.isEmpty()) {
                chatToUsers.remove(entry.getKey(), users);
            }
        }
    }
}
