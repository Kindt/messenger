package com.avandocmsg.messenger.api.metrics;

import io.prometheus.client.Counter;

/** FR-OPT-08: bytes not re-uploaded thanks to content-hash deduplication. */
public final class FileDedupMetrics {
    private static final Counter SAVED_BYTES = Counter.build()
        .name("storage_dedup_saved_bytes_total")
        .help("Bytes not stored again because an identical content hash already exists")
        .register();

    private FileDedupMetrics() {
    }

    public static void savedBytes(long bytes) {
        if (bytes > 0) {
            SAVED_BYTES.inc(bytes);
        }
    }
}
