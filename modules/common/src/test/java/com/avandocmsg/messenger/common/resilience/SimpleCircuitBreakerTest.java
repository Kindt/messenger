package com.avandocmsg.messenger.common.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleCircuitBreakerTest {

    @Test
    void opensAfterThresholdFailures() {
        var breaker = new SimpleCircuitBreaker(2, Duration.ofSeconds(30));
        breaker.recordFailure();
        assertTrue(breaker.allowCall());
        breaker.recordFailure();
        assertEquals(SimpleCircuitBreaker.State.OPEN, breaker.state());
        assertFalse(breaker.allowCall());
    }

    @Test
    void successResetsFailures() {
        var breaker = new SimpleCircuitBreaker(2, Duration.ofSeconds(30));
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();
        assertTrue(breaker.allowCall());
    }
}
