package com.avandocmsg.messenger.worker.deeparchive;

import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepArchiverMetricsTest {

    private static double sample(String name) {
        var v = CollectorRegistry.defaultRegistry.getSampleValue(name);
        return v != null ? v : 0.0;
    }

    @Test
    void chunkMetrics_areExportedAndIncremented() {
        double beforeWrites = sample("deep_archiver_chunk_writes_total");
        double beforeMsgs = sample("deep_archiver_chunked_messages_total");

        DeepArchiverMetrics.chunkWrite();
        DeepArchiverMetrics.chunkedMessage();

        var afterWrites = CollectorRegistry.defaultRegistry.getSampleValue("deep_archiver_chunk_writes_total");
        var afterMsgs = CollectorRegistry.defaultRegistry.getSampleValue("deep_archiver_chunked_messages_total");
        assertNotNull(afterWrites);
        assertNotNull(afterMsgs);
        assertTrue(afterWrites >= beforeWrites + 1.0);
        assertTrue(afterMsgs >= beforeMsgs + 1.0);
    }
}

