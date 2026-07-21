package com.avandocmsg.messenger.common.hotplug;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotPlugRegistryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void heartbeatMarksServicePresent_andEvictsWhenStale() {
        var registry = new HotPlugRegistry(1_000, MAPPER);
        long t0 = 1_000_000L;
        registry.onHeartbeat("indexer-1", "ACTIVE", t0);

        assertTrue(registry.isPresent("indexer-1", t0 + 500));
        assertFalse(registry.isPresent("indexer-1", t0 + 1_500));
        assertEquals(1, registry.evictStale(t0 + 1_500));
        assertFalse(registry.isPresent("indexer-1", t0 + 1_500));
    }

    @Test
    void payloadPath_updatesPresenceAndState() {
        var registry = new HotPlugRegistry(5_000, MAPPER);
        byte[] payload = """
            {"serviceId":"retention-1","state":"DRAINING","uptimeMs":12345}
            """.trim().getBytes();

        registry.onHeartbeatPayload(payload, 10_000L);

        var snapshot = registry.snapshot();
        assertTrue(snapshot.containsKey("retention-1"));
        assertEquals("DRAINING", snapshot.get("retention-1").state());
        assertTrue(registry.isPresent("retention-1", 12_000L));
    }

    @Test
    void enforceBounds_evictsOldestWhenOverMaxServices() {
        var registry = new HotPlugRegistry(60_000, 2, false, 0L, MAPPER);
        registry.onHeartbeat("svc-a", "ACTIVE", 1_000L);
        registry.onHeartbeat("svc-b", "ACTIVE", 2_000L);
        assertEquals(2, registry.snapshot().size());

        registry.onHeartbeat("svc-c", "ACTIVE", 3_000L);

        var snapshot = registry.snapshot();
        assertEquals(2, snapshot.size());
        assertFalse(snapshot.containsKey("svc-a"));
        assertTrue(snapshot.containsKey("svc-b"));
        assertTrue(snapshot.containsKey("svc-c"));
    }

    @Test
    void scheduledEviction_prunesStaleServicesWithoutExplicitCall() throws InterruptedException {
        var registry = new HotPlugRegistry(50, 256, true, 25L, MAPPER);
        try {
            long t0 = 1_000_000L;
            registry.onHeartbeat("indexer-1", "ACTIVE", t0);
            assertEquals(1, registry.snapshot().size());
            Thread.sleep(150); // NOSONAR java:S2925 -- wait for scheduled eviction tick in unit test
            assertEquals(0, registry.snapshot().size());
            assertFalse(registry.isPresent("indexer-1", t0 + 200));
        } finally {
            registry.close();
        }
    }
}
