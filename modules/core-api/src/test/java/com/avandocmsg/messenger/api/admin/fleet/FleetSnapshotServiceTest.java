package com.avandocmsg.messenger.api.admin.fleet;

import com.avandocmsg.messenger.api.admin.ui.AdminStatsPort;
import com.avandocmsg.messenger.api.admin.ui.dto.AdminServerStatsResponse;
import com.avandocmsg.messenger.api.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetSnapshotServiceTest {

    @Test
    void snapshot_includesLocalCoreApi() {
        AdminStatsPort stats = () -> new AdminServerStatsResponse(
            "0.0.1-SNAPSHOT",
            new AdminServerStatsResponse.JvmStats(100, 200, 300, 4, 5000),
            new AdminServerStatsResponse.DependencyHealth(true, true, false),
            new AdminServerStatsResponse.TableCounts(10, 5, 100, true),
            AdminServerStatsResponse.ExportCompliance.unavailable());
        var appConfig = new AppConfig() {
            @Override
            public String fleetAggregatorNode() {
                return "test-node";
            }

            @Override
            public int fleetProbeTimeoutMs() {
                return 500;
            }
        };
        var service = new FleetSnapshotService(
            FleetTargetRegistry.fromJson("[]"),
            stats,
            appConfig,
            null,
            30_000L
        );
        var snap = service.snapshot();
        assertEquals("test-node", snap.aggregatorNode());
        assertTrue(snap.components().stream().anyMatch(c -> "core-api".equals(c.role())));
        assertEquals(10, snap.sharedData().databaseCounts().users());
    }
}
