package com.avandocmsg.messenger.api.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

/** Read receipt API metrics on the default Prometheus registry. */
public final class ReadReceiptMetrics {

    private static final Counter INSERTS = Counter.build()
        .name("read_receipt_inserts_total")
        .help("Per-message read receipt rows inserted")
        .register();

    private static final Counter BATCH_COUNT = Counter.build()
        .name("read_receipt_batch_count")
        .help("Batch read receipt API calls")
        .register();

    private static final Histogram BATCH_SIZE = Histogram.build()
        .name("read_receipt_batch_size")
        .buckets(1, 10, 50, 100)
        .help("Number of message IDs in read-batch requests")
        .register();

    private static final Gauge REPOSITORY_SIZE = Gauge.build()
        .name("read_receipt_repository_size")
        .help("Rows in message_read_receipts (sampled on admin/metrics refresh)")
        .register();

    private ReadReceiptMetrics() {
    }

    public static void insertRecorded() {
        INSERTS.inc();
    }

    public static void batchRecorded(int size) {
        BATCH_COUNT.inc();
        BATCH_SIZE.observe(Math.max(0, size));
    }

    public static void setRepositorySize(long rows) {
        REPOSITORY_SIZE.set(Math.max(0, rows));
    }
}
