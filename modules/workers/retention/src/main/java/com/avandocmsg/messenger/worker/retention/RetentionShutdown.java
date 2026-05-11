package com.avandocmsg.messenger.worker.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Shared graceful-shutdown helpers for {@link RetentionWorker} (JVM hook).
 */
final class RetentionShutdown {
    private static final Logger log = LoggerFactory.getLogger(RetentionShutdown.class);

    /** Bounded wait for the scan {@link ScheduledExecutorService} during shutdown. */
    static final int DEFAULT_EXECUTOR_AWAIT_SECONDS = 15;

    private RetentionShutdown() {
    }

    /**
     * Closes each resource in iteration order; failures are logged at WARN and do not stop later closes.
     * Package-private for unit tests.
     */
    static void runCloseables(Iterable<? extends AutoCloseable> closeables) {
        for (var c : closeables) {
            if (c == null) {
                continue;
            }
            try {
                c.close();
            } catch (Exception e) {
                log.warn("Retention shutdown: failed closing {}", c.getClass().getName(), e);
            }
        }
    }

    static void shutdownScanExecutorQuietly(ScheduledExecutorService executor, int awaitSeconds) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(awaitSeconds, TimeUnit.SECONDS)) {
                log.warn(
                    "Retention shutdown: scan executor did not terminate within {}s (in-flight pass may still be running; closing resources best-effort)",
                    awaitSeconds
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Retention shutdown: interrupted while awaiting scan executor termination", e);
        }
    }
}
