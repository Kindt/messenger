package com.avandocmsg.messenger.worker.botdelivery;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotDeliveryDedupCacheTest {

    @Test
    void markIfFirst_blocksDuplicateWithinTtl() {
        var cache = new BotDeliveryDedupCache(Duration.ofMinutes(10));
        assertTrue(cache.markIfFirst("msg-1|https://example/hook"));
        assertFalse(cache.markIfFirst("msg-1|https://example/hook"));
    }

    @Test
    void markIfFirst_allowsDifferentKeys() {
        var cache = new BotDeliveryDedupCache(Duration.ofMinutes(10));
        assertTrue(cache.markIfFirst("msg-1|https://a/hook"));
        assertTrue(cache.markIfFirst("msg-1|https://b/hook"));
        assertTrue(cache.markIfFirst("msg-2|https://a/hook"));
    }

    @Test
    void markIfFirst_allowsRedeliveryAfterTtlExpires() throws InterruptedException {
        var cache = new BotDeliveryDedupCache(Duration.ofMillis(50));
        var key = "msg-1|https://example/hook";
        assertTrue(cache.markIfFirst(key));
        assertFalse(cache.markIfFirst(key));
        Thread.sleep(60);
        assertTrue(cache.markIfFirst(key));
    }

    @Test
    void markIfFirst_evictsOldestWhenOverMaxEntries() {
        var cache = new BotDeliveryDedupCache(Duration.ofHours(1));
        for (int i = 0; i < BotDeliveryDedupCache.MAX_ENTRIES + 1; i++) {
            cache.markIfFirst("k" + i + "|https://example/hook");
        }
        assertTrue(cache.size() <= BotDeliveryDedupCache.MAX_ENTRIES);
    }
}
