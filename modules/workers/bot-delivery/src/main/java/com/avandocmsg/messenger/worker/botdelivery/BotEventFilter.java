package com.avandocmsg.messenger.worker.botdelivery;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;

/** MENTIONS_ONLY vs READ_ALL filtering for bot webhook delivery. */
public final class BotEventFilter {
    private BotEventFilter() {
    }

    public static boolean shouldDeliver(MessageWorkerEvent event, String botName, String listenMode) {
        if (listenMode == null || "READ_ALL".equalsIgnoreCase(listenMode)) {
            return true;
        }
        if (event.encrypted()) {
            return false;
        }
        var text = event.searchText();
        if (text == null || text.isBlank() || botName == null || botName.isBlank()) {
            return false;
        }
        return text.contains("@" + botName);
    }
}
