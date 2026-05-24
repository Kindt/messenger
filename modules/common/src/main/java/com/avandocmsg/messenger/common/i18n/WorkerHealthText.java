package com.avandocmsg.messenger.common.i18n;

/** Localized readiness probe bodies for worker HTTP {@code /health}. */
public final class WorkerHealthText {

    private WorkerHealthText() {
    }

    public static String ok(UserMessageSource messages) {
        if (messages == null) {
            return "ok";
        }
        return messages.get("worker.health.ok");
    }

    public static String notReady(UserMessageSource messages) {
        if (messages == null) {
            return "not ready";
        }
        return messages.get("worker.health.not_ready");
    }
}
