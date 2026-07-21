package com.avandocmsg.messenger.api.metrics;

import io.prometheus.client.Counter;

/** Prometheus counters for {@link com.avandocmsg.messenger.core.port.ReadCachePort} (spec 006 FR-OPT-03). */
public final class ReadCacheMetrics {

    private static final Counter HITS = Counter.build()
        .name("read_cache_hit_total")
        .help("Read cache hits")
        .labelNames("kind")
        .register();

    private static final Counter MISSES = Counter.build()
        .name("read_cache_miss_total")
        .help("Read cache misses")
        .labelNames("kind")
        .register();

    private static final Counter INVALIDATION_FAILURES = Counter.build()
        .name("read_cache_invalidation_failure_total")
        .help("Read-cache NATS invalidation events that could not be processed")
        .labelNames("reason")
        .register();

    private ReadCacheMetrics() {
    }

    public static void hit(String kind) {
        HITS.labels(safeKind(kind)).inc();
    }

    public static void miss(String kind) {
        MISSES.labels(safeKind(kind)).inc();
    }

    public static void invalidationFailure(String reason) {
        INVALIDATION_FAILURES.labels(safeKind(reason)).inc();
    }

    public static void ensureRegistered() {
        // Touch counters so they appear before first scrape.
        HITS.labels("warmup");
        MISSES.labels("warmup");
        INVALIDATION_FAILURES.labels("warmup");
    }

    private static String safeKind(String kind) {
        return kind == null || kind.isBlank() ? "unknown" : kind;
    }
}
