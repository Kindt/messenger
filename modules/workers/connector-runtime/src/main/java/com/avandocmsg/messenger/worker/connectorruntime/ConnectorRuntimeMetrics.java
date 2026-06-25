package com.avandocmsg.messenger.worker.connectorruntime;

import io.prometheus.client.Info;

import java.util.concurrent.atomic.AtomicBoolean;

final class ConnectorRuntimeMetrics {

    private static final AtomicBoolean BUILD_INFO_LABELED = new AtomicBoolean(false);

    private static final Info BUILD_INFO = Info.build()
        .name("connector_runtime_worker_build_info")
        .help("Build information for the connector-runtime worker (labels: version, name).")
        .register();

    private ConnectorRuntimeMetrics() {
    }

    static void registerBuildInfoOnce() {
        if (BUILD_INFO_LABELED.compareAndSet(false, true)) {
            BUILD_INFO.info("version", "1", "name", "connector-runtime-worker");
        }
    }
}
