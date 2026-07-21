package com.avandocmsg.messenger.common.hotplug;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

/**
 * Shared Prometheus metrics for hot-plug lifecycle flow.
 *
 * <p>The default static methods publish to {@link CollectorRegistry#defaultRegistry}.
 * For tests or isolated registries, use {@link #forRegistry(CollectorRegistry)}.
 */
public final class HotPlugMetrics {

    private static final String UNKNOWN = "unknown";
    private static final String LABEL_SERVICE_ID = "service_id";
    private static final HotPlugMetrics DEFAULT = new HotPlugMetrics(CollectorRegistry.defaultRegistry);

    private final Counter heartbeatPublishTotal;
    private final Counter heartbeatReceiveTotal;
    private final Counter registryStaleServiceRemovalsTotal;
    private final Histogram lifecycleDrainDurationSeconds;

    private HotPlugMetrics(CollectorRegistry registry) {
        this.heartbeatPublishTotal = Counter.build()
            .name("hotplug_heartbeat_publish_total")
            .help("Heartbeat publishes from extracted service instances")
            .labelNames(LABEL_SERVICE_ID, "result")
            .register(registry);

        this.heartbeatReceiveTotal = Counter.build()
            .name("hotplug_heartbeat_receive_total")
            .help("Heartbeat receives processed by core registry")
            .labelNames(LABEL_SERVICE_ID, "state")
            .register(registry);

        this.registryStaleServiceRemovalsTotal = Counter.build()
            .name("hotplug_registry_stale_service_removals_total")
            .help("Services evicted from registry after heartbeat TTL expiration")
            .labelNames(LABEL_SERVICE_ID)
            .register(registry);

        this.lifecycleDrainDurationSeconds = Histogram.build()
            .name("hotplug_lifecycle_drain_duration_seconds")
            .help("Graceful shutdown drain duration in seconds")
            .labelNames(LABEL_SERVICE_ID, "result")
            .buckets(0.05, 0.1, 0.25, 0.5, 1, 2, 5, 10, 30, 60)
            .register(registry);
    }

    public static HotPlugMetrics forRegistry(CollectorRegistry registry) {
        return new HotPlugMetrics(registry);
    }

    public static void heartbeatPublished(String serviceId, boolean success) {
        DEFAULT.onHeartbeatPublished(serviceId, success);
    }

    public static void heartbeatReceived(String serviceId, String state) {
        DEFAULT.onHeartbeatReceived(serviceId, state);
    }

    public static void registryStaleServiceRemoved(String serviceId) {
        DEFAULT.onRegistryStaleServiceRemoved(serviceId);
    }

    public static void observeDrainDurationSeconds(String serviceId, double seconds, boolean success) {
        DEFAULT.onObserveDrainDurationSeconds(serviceId, seconds, success);
    }

    public void onHeartbeatPublished(String serviceId, boolean success) {
        heartbeatPublishTotal.labels(normalize(serviceId), success ? "success" : "error").inc();
    }

    public void onHeartbeatReceived(String serviceId, String state) {
        heartbeatReceiveTotal.labels(normalize(serviceId), normalize(state)).inc();
    }

    public void onRegistryStaleServiceRemoved(String serviceId) {
        registryStaleServiceRemovalsTotal.labels(normalize(serviceId)).inc();
    }

    public void onObserveDrainDurationSeconds(String serviceId, double seconds, boolean success) {
        if (seconds < 0) {
            return;
        }
        lifecycleDrainDurationSeconds.labels(normalize(serviceId), success ? "success" : "error").observe(seconds);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return value.trim();
    }
}
