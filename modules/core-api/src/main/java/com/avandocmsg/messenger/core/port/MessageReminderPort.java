package com.avandocmsg.messenger.core.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Per-user message reminders (spec 022 Phase 5). */
public interface MessageReminderPort {
    UUID create(CreateReminder cmd);

    Optional<ReminderRow> find(UUID id);

    List<ReminderRow> listForUser(UUID userId, int limit);

    List<ReminderRow> listDue(Instant now, int limit);

    boolean updateStatus(UUID id, String status);

    record CreateReminder(
        UUID userId,
        UUID chatId,
        UUID messageId,
        Instant remindAt
    ) {}

    record ReminderRow(
        UUID id,
        UUID userId,
        UUID chatId,
        UUID messageId,
        Instant remindAt,
        String status,
        Instant createdAt
    ) {}
}
