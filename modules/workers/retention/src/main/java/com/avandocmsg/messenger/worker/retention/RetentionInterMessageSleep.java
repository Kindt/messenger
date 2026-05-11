package com.avandocmsg.messenger.worker.retention;

/**
 * Throttle helper for hot-body janitor passes ({@link RetentionHotBodyJanitor}).
 */
final class RetentionInterMessageSleep {
    private RetentionInterMessageSleep() {
    }

    /**
     * @return {@code true} if sleep was interrupted ({@link Thread#interrupt()} restored on the current thread)
     */
    static boolean sleepQuiet(long delayMs) {
        if (delayMs <= 0) {
            return false;
        }
        try {
            Thread.sleep(delayMs);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
