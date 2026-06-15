package com.avandocmsg.messenger.worker.indexer;

import io.prometheus.client.Info;

import java.util.concurrent.atomic.AtomicBoolean;

/** Build info for {@link IndexerWorker}; operational counters live in {@link IndexerSolrMetrics}. */
final class IndexerMetrics {

    private static final AtomicBoolean BUILD_INFO_LABELED = new AtomicBoolean(false);

    private static final Info BUILD_INFO = Info.build()
        .name("indexer_worker_build_info")
        .help("Build information for the indexer worker (labels: version, name).")
        .register();

    private IndexerMetrics() {
    }

    static void registerBuildInfoOnce() {
        if (BUILD_INFO_LABELED.compareAndSet(false, true)) {
            BUILD_INFO.info("version", "1", "name", "indexer-worker");
        }
    }
}
