package com.avandocmsg.messenger.common.nats;

import io.prometheus.client.Histogram;

/** Prometheus fan-out metrics (PS-2.4). */
public final class PipelineFanoutMetrics {

    private static final Histogram RECIPIENTS = Histogram.build()
        .name("pipeline_fanout_recipients")
        .help("Recipient count per fan-out publish")
        .buckets(1, 2, 5, 10, 50, 256, 512, 1024, 2048)
        .register();

    private PipelineFanoutMetrics() {
    }

    public static void observeRecipients(int count) {
        if (count > 0) {
            RECIPIENTS.observe(count);
        }
    }
}
