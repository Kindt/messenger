package com.avandocmsg.messenger.core.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Outbound persistence for message @mentions. */
public interface MessageMentionRepositoryPort {
    record MentionRow(UUID userId, String kind) {}

    record MentionSummary(List<String> userIds, boolean mentionAll) {}

    void insertMentions(UUID messageId, List<MentionRow> rows);

    Map<UUID, MentionSummary> findSummariesByMessageIds(List<UUID> messageIds);

    boolean isUserMentioned(UUID messageId, UUID userId);

    boolean hasMentionAll(UUID messageId);
}
