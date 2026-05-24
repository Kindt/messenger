package com.avandocmsg.messenger.common.hotplug;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotPlugMetricsTest {

    @Test
    void countersAndHistogramAreRecorded() {
        var registry = new CollectorRegistry();
        var metrics = HotPlugMetrics.forRegistry(registry);

        metrics.onHeartbeatPublished("indexer-1", true);
        metrics.onHeartbeatReceived("indexer-1", "ACTIVE");
        metrics.onRegistryStaleServiceRemoved("indexer-1");
        metrics.onObserveDrainDurationSeconds("indexer-1", 2.5, true);

        assertEquals(
            1.0,
            registry.getSampleValue(
                "hotplug_heartbeat_publish_total",
                new String[] {"service_id", "result"},
                new String[] {"indexer-1", "success"}
            )
        );
        assertEquals(
            1.0,
            registry.getSampleValue(
                "hotplug_heartbeat_receive_total",
                new String[] {"service_id", "state"},
                new String[] {"indexer-1", "ACTIVE"}
            )
        );
        assertEquals(
            1.0,
            registry.getSampleValue(
                "hotplug_registry_stale_service_removals_total",
                new String[] {"service_id"},
                new String[] {"indexer-1"}
            )
        );
        assertEquals(
            1.0,
            registry.getSampleValue(
                "hotplug_lifecycle_drain_duration_seconds_count",
                new String[] {"service_id", "result"},
                new String[] {"indexer-1", "success"}
            )
        );
    }

    @Test
    void metricsAreExposedAndUnknownLabelsAreNormalized() throws IOException {
        var registry = new CollectorRegistry();
        var metrics = HotPlugMetrics.forRegistry(registry);

        metrics.onHeartbeatPublished(null, false);
        metrics.onHeartbeatReceived(" ", null);
        metrics.onRegistryStaleServiceRemoved("");
        metrics.onObserveDrainDurationSeconds(null, -1, false);
        metrics.onObserveDrainDurationSeconds(null, 0.2, false);

        assertEquals(
            1.0,
            registry.getSampleValue(
                "hotplug_heartbeat_publish_total",
                new String[] {"service_id", "result"},
                new String[] {"unknown", "error"}
            )
        );
        assertEquals(
            1.0,
            registry.getSampleValue(
                "hotplug_heartbeat_receive_total",
                new String[] {"service_id", "state"},
                new String[] {"unknown", "unknown"}
            )
        );
        assertEquals(
            1.0,
            registry.getSampleValue(
                "hotplug_registry_stale_service_removals_total",
                new String[] {"service_id"},
                new String[] {"unknown"}
            )
        );
        assertEquals(
            1.0,
            registry.getSampleValue(
                "hotplug_lifecycle_drain_duration_seconds_count",
                new String[] {"service_id", "result"},
                new String[] {"unknown", "error"}
            )
        );

        var writer = new StringWriter();
        TextFormat.write004(writer, registry.metricFamilySamples());
        var body = writer.toString();
        assertTrue(body.contains("hotplug_heartbeat_publish_total"));
        assertTrue(body.contains("hotplug_heartbeat_receive_total"));
        assertTrue(body.contains("hotplug_registry_stale_service_removals_total"));
        assertTrue(body.contains("hotplug_lifecycle_drain_duration_seconds"));
    }
}
