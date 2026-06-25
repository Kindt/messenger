package com.avandocmsg.messenger.worker.pipeline;

import io.prometheus.client.Info;

import java.util.concurrent.atomic.AtomicBoolean;

final class PipelineMetrics {

    private static final AtomicBoolean BUILD_INFO_LABELED = new AtomicBoolean(false);

    private static final Info BUILD_INFO = Info.build()
        .name("message_pipeline_worker_build_info")
        .help("Build information for the message-pipeline worker (labels: version, name).")
        .register();

    private PipelineMetrics() {
    }

    static void registerBuildInfoOnce() {
        if (BUILD_INFO_LABELED.compareAndSet(false, true)) {
            BUILD_INFO.info("version", "1", "name", "message-pipeline-worker");
        }
    }
}
