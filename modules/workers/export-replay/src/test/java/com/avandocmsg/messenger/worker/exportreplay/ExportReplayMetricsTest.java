package com.avandocmsg.messenger.worker.exportreplay;

import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportReplayMetricsTest {

    @Test
    void jobCancelled_incrementsCounter() {
        double before = sample("export_replay_worker_jobs_cancelled_total", new String[0], new String[0]);
        ExportReplayMetrics.jobCancelled();
        assertEquals(before + 1.0, sample("export_replay_worker_jobs_cancelled_total", new String[0], new String[0]), 0.001);
    }

    @Test
    void jobCompleted_labelsTerminalStatus() {
        double before = sample(
            "export_replay_worker_jobs_completed_total",
            new String[] { "terminal_status" },
            new String[] { "export_v1" });
        ExportReplayMetrics.jobCompleted("export_v1");
        assertEquals(before + 1.0, sample(
            "export_replay_worker_jobs_completed_total",
            new String[] { "terminal_status" },
            new String[] { "export_v1" }), 0.001);
    }

    private static double sample(String name, String[] labelNames, String[] labelValues) {
        return Optional.ofNullable(CollectorRegistry.defaultRegistry.getSampleValue(name, labelNames, labelValues))
            .orElse(0.0);
    }
}
