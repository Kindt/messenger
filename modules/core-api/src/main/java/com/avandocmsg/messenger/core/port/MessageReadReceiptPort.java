package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.api.chats.dto.ReadReceiptUserInfo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Per-message read receipts ({@code message_read_receipts}). */
public interface MessageReadReceiptPort {
    boolean insert(UUID messageId, UUID userId, Instant readAt);

    int insertBatch(List<UUID> messageIds, UUID userId, Instant readAt);

    List<ReadReceiptUserInfo> findByMessageId(UUID messageId, int offset, int limit);

    long countAll();

    int deleteOlderThanDays(int days);
}
