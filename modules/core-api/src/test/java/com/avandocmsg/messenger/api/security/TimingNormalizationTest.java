package com.avandocmsg.messenger.api.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TimingNormalizationTest {

    @Test
    void runWithMinimumDuration_padsShortExecution() {
        var minNs = 10_000_000L;
        var start = System.nanoTime();
        TimingNormalization.runWithMinimumDuration(minNs, () -> "ok");
        assertTrue(System.nanoTime() - start >= minNs * 0.8);
    }
}
