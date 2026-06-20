package com.avandocmsg.messenger.core.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Deferred message send queue (spec 022 Phase 5). */
public interface ScheduledMessagePort {
    UUID create(CreateScheduled cmd);

    Optional<ScheduledRow> find(UUID id);

    List<ScheduledRow> listForChat(UUID chatId, int limit);

    List<ScheduledRow> listDue(Instant now, int limit);

    boolean updateStatus(UUID id, String status, UUID sentMessageId);

    record CreateScheduled(
        UUID chatId,
        UUID senderId,
        String messageType,
        String content,
        Instant scheduledAt,
        UUID replyToMsgId,
        UUID threadId,
        String clientMsgId
    ) {}

    record ScheduledRow(
        UUID id,
        UUID chatId,
        UUID senderId,
        String messageType,
        String content,
        Instant scheduledAt,
        String status,
        UUID replyToMsgId,
        UUID threadId,
        String clientMsgId,
        UUID sentMessageId,
        Instant createdAt
    ) {}
}
