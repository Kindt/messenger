package com.avandocmsg.messenger.api.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guard for timing normalization helper (ROADMAP §5 / spec 014 S2-2). */
class TimingAttackPreventionTest {

    @Test
    void normalizationPreservesResult() {
        var value = TimingNormalization.runWithMinimumDuration(1_000_000L, () -> "secure");
        assertEquals("secure", value);
    }

    @Test
    void runWithMinimumDuration_padsFastPath() {
        var minNanos = 5_000_000L;
        var start = System.nanoTime();
        TimingNormalization.runWithMinimumDuration(minNanos, () -> "ok");
        var elapsed = System.nanoTime() - start;
        assertTrue(elapsed >= minNanos - 500_000L,
            "expected at least ~" + minNanos + "ns, got " + elapsed);
    }

    @Test
    void padNotFoundExtra_addsDelayWhenConfigured() {
        var extra = 3_000_000L;
        var start = System.nanoTime();
        TimingNormalization.padNotFoundExtra(extra);
        var elapsed = System.nanoTime() - start;
        assertTrue(elapsed >= extra - 500_000L);
    }
}
