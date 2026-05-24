package com.avandocmsg.messenger.common.hotplug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotPlugRegistryTest {

    @Test
    void heartbeatMarksServicePresent_andEvictsWhenStale() {
        var registry = new HotPlugRegistry(1_000);
        long t0 = 1_000_000L;
        registry.onHeartbeat("indexer-1", "ACTIVE", t0);

        assertTrue(registry.isPresent("indexer-1", t0 + 500));
        assertFalse(registry.isPresent("indexer-1", t0 + 1_500));
        assertEquals(1, registry.evictStale(t0 + 1_500));
        assertFalse(registry.isPresent("indexer-1", t0 + 1_500));
    }

    @Test
    void payloadPath_updatesPresenceAndState() {
        var registry = new HotPlugRegistry(5_000);
        byte[] payload = """
            {"serviceId":"retention-1","state":"DRAINING","uptimeMs":12345}
            """.trim().getBytes();

        registry.onHeartbeatPayload(payload, 10_000L);

        var snapshot = registry.snapshot();
        assertTrue(snapshot.containsKey("retention-1"));
        assertEquals("DRAINING", snapshot.get("retention-1").state());
        assertTrue(registry.isPresent("retention-1", 12_000L));
    }
}
