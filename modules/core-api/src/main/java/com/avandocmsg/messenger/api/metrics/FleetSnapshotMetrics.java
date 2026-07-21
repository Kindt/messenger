package com.avandocmsg.messenger.api.metrics;

import com.avandocmsg.messenger.api.admin.fleet.dto.FleetSnapshotResponse;
import io.prometheus.client.Gauge;

/** Prometheus gauges mirroring admin fleet snapshot (FLEET-07 / Grafana). */
public final class FleetSnapshotMetrics {

    private static final Gauge COMPONENTS_TOTAL = Gauge.build()
        .name("fleet_components_total")
        .help("Components in last fleet snapshot")
        .register();

    private static final Gauge COMPONENTS_READY = Gauge.build()
        .name("fleet_components_ready")
        .help("Ready components in last fleet snapshot")
        .register();

    private static final Gauge COMPONENT_READY = Gauge.build()
        .name("fleet_component_ready")
        .labelNames("component_id", "role", "source")
        .help("1 if component ready in last fleet snapshot")
        .register();

    private static final Gauge COMPONENT_LATENCY_MS = Gauge.build()
        .name("fleet_component_probe_latency_ms")
        .labelNames("component_id", "role")
        .help("HTTP probe latency from last fleet snapshot")
        .register();

    private FleetSnapshotMetrics() {
    }

    public static void recordSnapshot(FleetSnapshotResponse snapshot) {
        if (snapshot == null || snapshot.components() == null) {
            return;
        }
        int ready = 0;
        for (var c : snapshot.components()) {
            if (Boolean.TRUE.equals(c.ready())) {
                ready++;
            }
            var id = safeLabel(c.id());
            var role = safeLabel(c.role());
            var source = safeLabel(c.source());
            COMPONENT_READY.labels(id, role, source).set(Boolean.TRUE.equals(c.ready()) ? 1 : 0);
            if (c.latencyMs() != null) {
                COMPONENT_LATENCY_MS.labels(id, role).set(c.latencyMs());
            }
        }
        COMPONENTS_TOTAL.set(snapshot.components().size());
        COMPONENTS_READY.set(ready);
    }

    private static String safeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }
}
