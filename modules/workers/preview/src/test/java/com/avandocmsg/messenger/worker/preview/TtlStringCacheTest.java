package com.avandocmsg.messenger.worker.preview;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TtlStringCacheTest {

    @Test
    void putThenGet_returnsValue() {
        var cache = new TtlStringCache(Duration.ofHours(1));
        cache.put("k", "v");
        assertEquals("v", cache.get("k").orElseThrow());
    }

    @Test
    void get_missingKey_empty() {
        var cache = new TtlStringCache(Duration.ofHours(1));
        assertTrue(cache.get("nope").isEmpty());
    }

    @Test
    void put_evictsOldestWhenOverMaxEntries() {
        var cache = new TtlStringCache(Duration.ofHours(1));
        for (int i = 0; i < 10_001; i++) {
            cache.put("k" + i, "v" + i);
        }
        assertTrue(cache.size() <= 10_000);
    }
}
