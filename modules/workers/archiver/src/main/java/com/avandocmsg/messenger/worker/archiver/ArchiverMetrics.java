package com.avandocmsg.messenger.worker.archiver;

import io.prometheus.client.Info;

import java.util.concurrent.atomic.AtomicBoolean;

final class ArchiverMetrics {

    private static final AtomicBoolean BUILD_INFO_LABELED = new AtomicBoolean(false);

    private static final Info BUILD_INFO = Info.build()
        .name("archiver_worker_build_info")
        .help("Build information for the archiver worker (labels: version, name).")
        .register();

    private ArchiverMetrics() {
    }

    static void registerBuildInfoOnce() {
        if (BUILD_INFO_LABELED.compareAndSet(false, true)) {
            BUILD_INFO.info("version", "1", "name", "archiver-worker");
        }
    }
}
