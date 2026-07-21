package com.avandocmsg.messenger.common.resilience;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight fail-fast circuit breaker for outbound HTTP/integration hot paths (FR-065, FR-102).
 */
public final class SimpleCircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long openDurationMs;
    private volatile State state = State.CLOSED;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long openUntilEpochMs = 0L;

    public SimpleCircuitBreaker(int failureThreshold, Duration openDuration) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = openDuration != null && !openDuration.isNegative()
            ? openDuration.toMillis()
            : Duration.ofSeconds(30).toMillis();
    }

    public State state() {
        refreshOpenState();
        return state;
    }

    public boolean allowCall() {
        refreshOpenState();
        return state != State.OPEN;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        state = State.CLOSED;
        openUntilEpochMs = 0L;
    }

    public void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            state = State.OPEN;
            openUntilEpochMs = System.currentTimeMillis() + openDurationMs;
        }
    }

    private void refreshOpenState() {
        if (state == State.OPEN && System.currentTimeMillis() >= openUntilEpochMs) {
            state = State.HALF_OPEN;
        }
    }
}
