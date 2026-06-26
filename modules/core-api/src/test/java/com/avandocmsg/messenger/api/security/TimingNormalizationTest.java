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

    @Test
    void padNotFoundExtra_noOpWhenZero() {
        var start = System.nanoTime();
        TimingNormalization.padNotFoundExtra(0);
        // Upper bound only guards against accidental sleep on zero; CI runners can be noisy.
        assertTrue(System.nanoTime() - start < 50_000_000L);
    }
}
