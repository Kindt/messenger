package com.avandocmsg.messenger.common.hotplug;

import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Publishes hot-plug heartbeat messages to NATS on a fixed interval.
 */
public final class HotPlugHeartbeat implements AutoCloseable {

    private final Connection nats;
    private final String serviceId;
    private final long intervalMs;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final long startedAtMs;
    private volatile ScheduledFuture<?> task;

    public HotPlugHeartbeat(Connection nats, String serviceId, long intervalMs) {
        this(nats, serviceId, intervalMs, Clock.systemUTC(), new ObjectMapper());
    }

    HotPlugHeartbeat(Connection nats, String serviceId, long intervalMs, Clock clock, ObjectMapper mapper) {
        this.nats = Objects.requireNonNull(nats, "nats");
        this.serviceId = normalizeServiceId(serviceId);
        this.intervalMs = Math.max(1000L, intervalMs);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "hotplug-heartbeat-" + this.serviceId);
            t.setDaemon(true);
            return t;
        });
        this.startedAtMs = this.clock.millis();
    }

    public synchronized void start() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        task = scheduler.scheduleAtFixedRate(() -> publish("ACTIVE"), 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    public void publish(String state) {
        var uptime = Math.max(0L, clock.millis() - startedAtMs);
        var event = new HotPlugHeartbeatEvent(serviceId, normalizeState(state), uptime);
        var subject = NatsSubjects.SVC_HEARTBEAT_PREFIX + serviceId;
        try {
            var payload = mapper.writeValueAsBytes(event);
            nats.publish(subject, payload);
            HotPlugMetrics.heartbeatPublished(serviceId, true);
        } catch (JsonProcessingException e) {
            HotPlugMetrics.heartbeatPublished(serviceId, false);
            throw new IllegalStateException("Failed to serialize heartbeat event", e);
        } catch (RuntimeException e) {
            HotPlugMetrics.heartbeatPublished(serviceId, false);
            throw e;
        }
    }

    public HotPlugHeartbeatEvent parse(byte[] payload) {
        try {
            return mapper.readValue(payload, HotPlugHeartbeatEvent.class);
        } catch (Exception e) {
            var raw = payload == null ? "" : new String(payload, StandardCharsets.UTF_8);
            throw new IllegalArgumentException("Failed to parse heartbeat payload: " + raw, e);
        }
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdownNow();
    }

    private static String normalizeServiceId(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return "unknown";
        }
        return serviceId.trim();
    }

    private static String normalizeState(String state) {
        if (state == null || state.isBlank()) {
            return "UNKNOWN";
        }
        return state.trim();
    }
}
