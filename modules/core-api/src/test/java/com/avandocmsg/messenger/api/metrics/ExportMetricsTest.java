package com.avandocmsg.messenger.api.metrics;

import com.avandocmsg.messenger.api.export.ExportJobCancelSupport;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportMetricsTest {

    @Test
    void jobCancelled_incrementsLabeledCounter() {
        double before = sample(
            "export_jobs_cancelled_total",
            new String[] { "source", "previous_status" },
            new String[] { "admin", "processing" });
        ExportMetrics.jobCancelled(ExportJobCancelSupport.AUDIT_ADMIN_CANCEL, "processing");
        assertEquals(before + 1.0, sample(
            "export_jobs_cancelled_total",
            new String[] { "source", "previous_status" },
            new String[] { "admin", "processing" }), 0.001);
    }

    @Test
    void jobEnqueued_mapsTriggerToSource() {
        assertEquals("admin", ExportMetrics.enqueueSource("admin_api"));
        assertEquals("auto", ExportMetrics.enqueueSource("retention_suggested"));
        assertEquals("user", ExportMetrics.enqueueSource("api"));
    }

    private static double sample(String name, String[] labelNames, String[] labelValues) {
        return Optional.ofNullable(CollectorRegistry.defaultRegistry.getSampleValue(name, labelNames, labelValues))
            .orElse(0.0);
    }
}
