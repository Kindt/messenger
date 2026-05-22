package com.avandocmsg.messenger.worker.push;

final class WebPushErrors {

    private WebPushErrors() {
    }

    static boolean isExpiredSubscription(Throwable error) {
        for (var t = error; t != null; t = t.getCause()) {
            var msg = t.getMessage();
            if (msg != null && (msg.contains(" 410 ") || msg.contains("status=410") || msg.contains("code=410")
                || msg.contains("410 Gone"))) {
                return true;
            }
        }
        return false;
    }
}
