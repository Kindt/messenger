package com.avandocmsg.messenger.worker.retention;

import com.avandocmsg.messenger.common.concurrent.InterruptibleWait;

/**
 * Throttle helper for hot-body janitor passes ({@link RetentionHotBodyJanitor}).
 */
final class RetentionInterMessageSleep {
    private RetentionInterMessageSleep() {
    }

    /**
     * @return {@code true} if wait was interrupted
     */
    static boolean sleepQuiet(long delayMs) {
        return InterruptibleWait.sleepMillis(delayMs);
    }
}
