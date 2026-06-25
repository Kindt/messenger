package com.avandocmsg.messenger.worker.botdelivery;

import io.prometheus.client.Info;

import java.util.concurrent.atomic.AtomicBoolean;

final class BotDeliveryMetrics {

    private static final AtomicBoolean BUILD_INFO_LABELED = new AtomicBoolean(false);

    private static final Info BUILD_INFO = Info.build()
        .name("bot_delivery_worker_build_info")
        .help("Build information for the bot-delivery worker (labels: version, name).")
        .register();

    private BotDeliveryMetrics() {
    }

    static void registerBuildInfoOnce() {
        if (BUILD_INFO_LABELED.compareAndSet(false, true)) {
            BUILD_INFO.info("version", "1", "name", "bot-delivery-worker");
        }
    }
}
