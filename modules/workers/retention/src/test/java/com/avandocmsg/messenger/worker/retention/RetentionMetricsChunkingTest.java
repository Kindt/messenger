package com.avandocmsg.messenger.worker.retention;

import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionMetricsChunkingTest {

    private static double sample(String name) {
        var v = CollectorRegistry.defaultRegistry.getSampleValue(name);
        return v != null ? v : 0.0;
    }

    @Test
    void chunkAndFileRefMetrics_areExportedAndIncremented() {
        double beforeChunks = sample("retention_worker_chunk_writes_total");
        double beforeFileRefs = sample("retention_worker_file_ref_skipped_total");

        RetentionMetrics.chunkWrite();
        RetentionMetrics.fileRefSkipped();

        var afterChunks = CollectorRegistry.defaultRegistry.getSampleValue("retention_worker_chunk_writes_total");
        var afterFileRefs = CollectorRegistry.defaultRegistry.getSampleValue("retention_worker_file_ref_skipped_total");
        assertNotNull(afterChunks);
        assertNotNull(afterFileRefs);
        assertTrue(afterChunks >= beforeChunks + 1.0);
        assertTrue(afterFileRefs >= beforeFileRefs + 1.0);
    }
}
