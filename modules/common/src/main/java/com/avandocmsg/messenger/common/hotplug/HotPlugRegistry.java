package com.avandocmsg.messenger.common.hotplug;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory registry for extracted service heartbeat tracking.
 */
public final class HotPlugRegistry implements AutoCloseable {

    /** Upper bound on distinct service IDs (FR-023). */
    static final int DEFAULT_MAX_SERVICES = 256;
    static final long MIN_EVICT_INTERVAL_MS = 1_000L;

    private final long ttlMs;
    private final int maxServices;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, ServicePresence> services = new ConcurrentHashMap<>();
    private final ScheduledExecutorService evictionScheduler;

    public HotPlugRegistry(long ttlMs) {
        this(ttlMs, DEFAULT_MAX_SERVICES, true, evictIntervalMs(ttlMs), MessengerJson.mapper());
    }

    HotPlugRegistry(long ttlMs, ObjectMapper mapper) {
        this(ttlMs, DEFAULT_MAX_SERVICES, false, 0L, mapper);
    }

    HotPlugRegistry(long ttlMs, int maxServices, boolean scheduleEviction, long evictIntervalMs, ObjectMapper mapper) {
        this.ttlMs = Math.max(1000L, ttlMs);
        this.maxServices = Math.max(1, maxServices);
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        if (scheduleEviction) {
            this.evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(r, "hotplug-registry-evict");
                t.setDaemon(true);
                return t;
            });
            long intervalMs = evictIntervalMs > 0
                ? Math.max(1L, evictIntervalMs)
                : Math.max(MIN_EVICT_INTERVAL_MS, evictIntervalMs(this.ttlMs));
            evictionScheduler.scheduleAtFixedRate(
                () -> evictStale(System.currentTimeMillis()),
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS
            );
        } else {
            this.evictionScheduler = null;
        }
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
        enforceBounds(nowMs);
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

    public int maxServices() {
        return maxServices;
    }

    @Override
    public void close() {
        if (evictionScheduler != null) {
            evictionScheduler.shutdownNow();
        }
    }

    private void enforceBounds(long nowMs) {
        evictStale(nowMs);
        while (services.size() > maxServices) {
            String oldestKey = null;
            ServicePresence oldest = null;
            for (var entry : services.entrySet()) {
                var p = entry.getValue();
                if (oldest == null || p.lastHeartbeatEpochMs() < oldest.lastHeartbeatEpochMs()) {
                    oldestKey = entry.getKey();
                    oldest = p;
                }
            }
            if (oldestKey == null || oldest == null) {
                break;
            }
            if (services.remove(oldestKey, oldest)) {
                HotPlugMetrics.registryStaleServiceRemoved(oldestKey);
            }
        }
    }

    private static long evictIntervalMs(long ttlMs) {
        return Math.max(MIN_EVICT_INTERVAL_MS, Math.max(1000L, ttlMs) / 2);
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
