package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Batch-loads thread reply counts for main timeline (hex adapter). */
final class MessageThreadReplyEnrichment {
    private MessageThreadReplyEnrichment() {
    }

    static void attach(DataSource readDataSource, List<MessageResponse> messages) {
        if (messages == null || messages.isEmpty() || readDataSource == null) {
            return;
        }
        var rootIds = collectMessageIds(messages);
        if (rootIds.isEmpty()) {
            return;
        }
        var counts = loadReplyCounts(readDataSource, rootIds);
        if (counts.isEmpty()) {
            return;
        }
        applyCounts(messages, counts);
    }

    private static List<UUID> collectMessageIds(List<MessageResponse> messages) {
        var rootIds = new ArrayList<UUID>(messages.size());
        for (var m : messages) {
            try {
                rootIds.add(UUID.fromString(m.id()));
            } catch (IllegalArgumentException ignored) {
                // skip malformed message id
            }
        }
        return rootIds;
    }

    private static Map<UUID, Integer> loadReplyCounts(DataSource readDataSource, List<UUID> rootIds) {
        var sql = new StringBuilder(
            "SELECT thread_id, COUNT(*) AS cnt FROM messages WHERE deleted = false AND thread_id IN (");
        for (int i = 0; i < rootIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(") GROUP BY thread_id");
        var counts = new HashMap<UUID, Integer>();
        try (var conn = readDataSource.getConnection();
             var stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            for (var id : rootIds) {
                stmt.setObject(idx++, id);
            }
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var rootId = rs.getObject("thread_id", UUID.class);
                    var cnt = rs.getObject("cnt");
                    if (rootId != null && cnt instanceof Number n) {
                        counts.put(rootId, n.intValue());
                    }
                }
            }
        } catch (SQLException ignored) {
            return Map.of();
        }
        return counts;
    }

    private static void applyCounts(List<MessageResponse> messages, Map<UUID, Integer> counts) {
        for (int i = 0; i < messages.size(); i++) {
            var m = messages.get(i);
            try {
                var count = counts.get(UUID.fromString(m.id()));
                if (count == null) {
                    continue;
                }
                messages.set(i, new MessageResponse(
                    m.id(), m.chatId(), m.senderId(), m.type(), m.content(), m.replyToMsgId(),
                    m.deleted(), m.createdAt(), m.editedAt(), m.visibilityTtlSeconds(), m.attachmentFileId(),
                    m.threadId(), count,
                    m.mentionUserIds(), m.mentionAll(),
                    m.durationMs(), m.linkPreview(), m.replyPreview()));
            } catch (IllegalArgumentException ignored) {
                // skip malformed message id
            }
        }
    }
}
