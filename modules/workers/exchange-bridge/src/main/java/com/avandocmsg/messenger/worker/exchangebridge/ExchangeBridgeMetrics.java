package com.avandocmsg.messenger.worker.exchangebridge;

import io.prometheus.client.Info;

import java.util.concurrent.atomic.AtomicBoolean;

final class ExchangeBridgeMetrics {

    private static final AtomicBoolean BUILD_INFO_LABELED = new AtomicBoolean(false);

    private static final Info BUILD_INFO = Info.build()
        .name("exchange_bridge_worker_build_info")
        .help("Build information for the exchange-bridge worker (labels: version, name).")
        .register();

    private ExchangeBridgeMetrics() {
    }

    static void registerBuildInfoOnce() {
        if (BUILD_INFO_LABELED.compareAndSet(false, true)) {
            BUILD_INFO.info("version", "1", "name", "exchange-bridge-worker");
        }
    }
}
