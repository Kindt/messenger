package com.avandocmsg.messenger.api.metrics;

import com.avandocmsg.messenger.api.admin.fleet.dto.FleetSnapshotResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class FleetSnapshotMetricsTest {

    @Test
    void record_acceptsSnapshot() {
        var snap = new FleetSnapshotResponse(
            Instant.now(),
            "node-a",
            500,
            List.of(
                new FleetSnapshotResponse.FleetComponentSnapshot(
                    "core-api",
                    "core-api",
                    "local-jvm",
                    null,
                    null,
                    true,
                    true,
                    200,
                    1L,
                    null,
                    "ACTIVE",
                    1000L,
                    null,
                    1000L,
                    42L,
                    new FleetSnapshotResponse.Dependencies(true, true, true)
                )
            ),
            new FleetSnapshotResponse.SharedData(
                "0.0.1-SNAPSHOT",
                new FleetSnapshotResponse.DatabaseCounts(1, 2, 3, true)
            )
        );
        assertDoesNotThrow(() -> FleetSnapshotMetrics.recordSnapshot(snap));
    }
}
