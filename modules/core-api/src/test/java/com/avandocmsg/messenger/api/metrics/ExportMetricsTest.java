package com.avandocmsg.messenger.api.metrics;

import com.avandocmsg.messenger.api.export.ExportJobCancelSupport;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void countersExposeZeroSeriesAtStartup() {
        ExportMetrics.ensureRegistered();
        assertEquals(0.0, sample(
            "export_jobs_enqueued_total",
            new String[] { "source" },
            new String[] { "user" }));
        assertEquals(0.0, sample(
            "export_jobs_cancelled_total",
            new String[] { "source", "previous_status" },
            new String[] { "user", "queued" }));
    }

    @Test
    void countersAppearInTextExpositionBeforeFirstEvent() throws Exception {
        ExportMetrics.ensureRegistered();
        var writer = new StringWriter();
        TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
        var text = writer.toString();
        assertTrue(text.contains("export_jobs_enqueued_total{source=\"user\",}"));
        assertTrue(text.contains("export_jobs_cancelled_total{source=\"user\",previous_status=\"queued\",}"));
        assertTrue(text.contains("export_jobs_processing_stale") || text.contains("export_jobs_cancel_rejected_total"));
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
