package com.avandocmsg.messenger.core.port;

import java.util.UUID;

/** Aggregated per-chat read cursor ({@code chat_read_state}). */
public interface ChatReadStatePort {
    boolean upsertLastRead(UUID userId, UUID chatId, UUID lastReadMessageId);

    int countUnreadFromOthers(UUID userId, UUID chatId);
}
