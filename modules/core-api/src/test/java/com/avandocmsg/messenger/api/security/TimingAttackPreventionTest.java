package com.avandocmsg.messenger.api.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Guard for timing normalization helper (ROADMAP §5). */
class TimingAttackPreventionTest {

    @Test
    void normalizationPreservesResult() {
        var value = TimingNormalization.runWithMinimumDuration(1_000_000L, () -> "secure");
        assertEquals("secure", value);
    }
}
