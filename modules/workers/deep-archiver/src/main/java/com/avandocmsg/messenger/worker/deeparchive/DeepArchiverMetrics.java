package com.avandocmsg.messenger.worker.deeparchive;

import io.prometheus.client.Counter;
import io.prometheus.client.Info;

import java.util.concurrent.atomic.AtomicBoolean;

final class DeepArchiverMetrics {

    private static final AtomicBoolean BUILD_INFO_LABELED = new AtomicBoolean(false);

    private static final Info BUILD_INFO = Info.build()
        .name("deep_archiver_worker_build_info")
        .help("Build information for the deep-archiver worker (labels: version, name).")
        .register();

    private static final Counter CHUNK_WRITES = Counter.build()
        .name("deep_archiver_chunk_writes_total")
        .help("Total chunk part writes to MinIO during deep-archive chunking")
        .register();

    private static final Counter CHUNKED_MESSAGES = Counter.build()
        .name("deep_archiver_chunked_messages_total")
        .help("Messages stored as chunked deep-archive (multi-part) in MinIO")
        .register();

    private static final Counter BYTES_SAVED = Counter.build()
        .name("deep_archive_bytes_saved_total")
        .help("Bytes saved by deep-archive compression vs plain JSON")
        .register();

    private DeepArchiverMetrics() {
    }

    static void registerBuildInfoOnce() {
        if (BUILD_INFO_LABELED.compareAndSet(false, true)) {
            BUILD_INFO.info("version", "1", "name", "deep-archiver-worker");
        }
    }

    static void chunkWrite() {
        CHUNK_WRITES.inc();
    }

    static void chunkedMessage() {
        CHUNKED_MESSAGES.inc();
    }

    static void bytesSaved(long delta) {
        if (delta > 0) {
            BYTES_SAVED.inc(delta);
        }
    }
}
