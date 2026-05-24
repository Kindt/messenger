package com.avandocmsg.messenger.common.hotplug;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry for extracted service heartbeat tracking.
 */
public final class HotPlugRegistry {

    private final long ttlMs;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, ServicePresence> services = new ConcurrentHashMap<>();

    public HotPlugRegistry(long ttlMs) {
        this(ttlMs, new ObjectMapper());
    }

    HotPlugRegistry(long ttlMs, ObjectMapper mapper) {
        this.ttlMs = Math.max(1000L, ttlMs);
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public void onHeartbeat(HotPlugHeartbeatEvent event) {
        if (event == null) {
            return;
        }
        onHeartbeat(event.serviceId(), event.state(), System.currentTimeMillis());
    }

    public void onHeartbeat(String serviceId, String state, long nowMs) {
        var sid = normalize(serviceId);
        var normalizedState = normalize(state);
        services.put(sid, new ServicePresence(sid, normalizedState, nowMs));
        HotPlugMetrics.heartbeatReceived(sid, normalizedState);
    }

    public void onHeartbeatPayload(byte[] payload, long nowMs) {
        try {
            var event = mapper.readValue(payload, HotPlugHeartbeatEvent.class);
            onHeartbeat(event.serviceId(), event.state(), nowMs);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid hot-plug heartbeat payload", e);
        }
    }

    public boolean isPresent(String serviceId) {
        return isPresent(serviceId, System.currentTimeMillis());
    }

    public boolean isPresent(String serviceId, long nowMs) {
        var sid = normalize(serviceId);
        var p = services.get(sid);
        return p != null && nowMs - p.lastHeartbeatEpochMs() <= ttlMs;
    }

    public int evictStale(long nowMs) {
        int removed = 0;
        for (var entry : services.entrySet()) {
            var p = entry.getValue();
            if (nowMs - p.lastHeartbeatEpochMs() > ttlMs && services.remove(entry.getKey(), p)) {
                removed++;
                HotPlugMetrics.registryStaleServiceRemoved(entry.getKey());
            }
        }
        return removed;
    }

    public Map<String, ServicePresence> snapshot() {
        return Map.copyOf(services);
    }

    public long ttlMs() {
        return ttlMs;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }

    public record ServicePresence(
        String serviceId,
        String state,
        long lastHeartbeatEpochMs
    ) {
    }
}
