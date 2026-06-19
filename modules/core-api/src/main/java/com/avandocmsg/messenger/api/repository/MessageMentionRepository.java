package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageMentionRepositoryAdapter;
import com.avandocmsg.messenger.core.port.MessageMentionRepositoryPort;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Legacy façade for mention JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcMessageMentionRepositoryAdapter}.
 */
public class MessageMentionRepository {
    private final MessageMentionRepositoryPort port;

    public MessageMentionRepository(DataSource dataSource) {
        this.port = new JdbcMessageMentionRepositoryAdapter(dataSource);
    }

    MessageMentionRepository(MessageMentionRepositoryPort port) {
        this.port = port;
    }

    public record MentionRow(UUID userId, String kind) {}

    public record MentionSummary(List<String> userIds, boolean mentionAll) {}

    public void insertMentions(UUID messageId, List<MentionRow> rows) {
        var mapped = rows.stream()
            .map(r -> new MessageMentionRepositoryPort.MentionRow(r.userId(), r.kind()))
            .toList();
        port.insertMentions(messageId, mapped);
    }

    public Map<UUID, MentionSummary> findSummariesByMessageIds(List<UUID> messageIds) {
        var summaries = port.findSummariesByMessageIds(messageIds);
        var out = new java.util.HashMap<UUID, MentionSummary>();
        for (var entry : summaries.entrySet()) {
            var s = entry.getValue();
            out.put(entry.getKey(), new MentionSummary(s.userIds(), s.mentionAll()));
        }
        return out;
    }

    public Map<UUID, List<String>> findUserIdsByMessageIds(List<UUID> messageIds) {
        var summaries = findSummariesByMessageIds(messageIds);
        var out = new java.util.HashMap<UUID, List<String>>();
        for (var entry : summaries.entrySet()) {
            out.put(entry.getKey(), entry.getValue().userIds());
        }
        return out;
    }

    public boolean isUserMentioned(UUID messageId, UUID userId) {
        return port.isUserMentioned(messageId, userId);
    }

    public boolean hasMentionAll(UUID messageId) {
        return port.hasMentionAll(messageId);
    }
}
