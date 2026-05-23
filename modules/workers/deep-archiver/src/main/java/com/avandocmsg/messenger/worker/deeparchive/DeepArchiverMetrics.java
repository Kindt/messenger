package com.avandocmsg.messenger.worker.deeparchive;

import io.prometheus.client.Counter;

final class DeepArchiverMetrics {

    private static final Counter CHUNK_WRITES = Counter.build()
        .name("deep_archiver_chunk_writes_total")
        .help("Total chunk part writes to MinIO during deep-archive chunking")
        .register();

    private static final Counter CHUNKED_MESSAGES = Counter.build()
        .name("deep_archiver_chunked_messages_total")
        .help("Messages stored as chunked deep-archive (multi-part) in MinIO")
        .register();

    private DeepArchiverMetrics() {
    }

    static void chunkWrite() {
        CHUNK_WRITES.inc();
    }

    static void chunkedMessage() {
        CHUNKED_MESSAGES.inc();
    }
}
