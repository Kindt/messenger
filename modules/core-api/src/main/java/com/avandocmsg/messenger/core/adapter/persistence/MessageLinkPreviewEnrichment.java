package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Attaches link previews to list reads (hex adapter). */
final class MessageLinkPreviewEnrichment {
    private MessageLinkPreviewEnrichment() {
    }

    static void attach(DataSource readDataSource, List<MessageResponse> messages) {
        if (messages == null || messages.isEmpty() || readDataSource == null) {
            return;
        }
        var ids = collectMessageIds(messages);
        if (ids.isEmpty()) {
            return;
        }
        var previews = loadPreviews(readDataSource, ids);
        if (previews.isEmpty()) {
            return;
        }
        applyPreviews(messages, previews);
    }

    private static List<UUID> collectMessageIds(List<MessageResponse> messages) {
        var ids = new ArrayList<UUID>(messages.size());
        for (var m : messages) {
            try {
                ids.add(UUID.fromString(m.id()));
            } catch (IllegalArgumentException ignored) {
                // skip malformed message id
            }
        }
        return ids;
    }

    private static Map<UUID, com.avandocmsg.messenger.api.messages.dto.MessageLinkPreview> loadPreviews(
        DataSource readDataSource, List<UUID> ids
    ) {
        var sql = new StringBuilder(
            "SELECT message_id, url, title FROM message_link_previews WHERE message_id IN (");
        for (int i = 0; i < ids.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(")");
        var previews = new HashMap<UUID, com.avandocmsg.messenger.api.messages.dto.MessageLinkPreview>();
        try (var conn = readDataSource.getConnection();
             var stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            for (var id : ids) {
                stmt.setObject(idx++, id);
            }
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var msgId = rs.getObject("message_id", UUID.class);
                    previews.put(msgId, new com.avandocmsg.messenger.api.messages.dto.MessageLinkPreview(
                        rs.getString("url"),
                        rs.getString("title")));
                }
            }
        } catch (SQLException ignored) {
            return Map.of();
        }
        return previews;
    }

    private static void applyPreviews(
        List<MessageResponse> messages,
        Map<UUID, com.avandocmsg.messenger.api.messages.dto.MessageLinkPreview> previews
    ) {
        for (int i = 0; i < messages.size(); i++) {
            var m = messages.get(i);
            try {
                var preview = previews.get(UUID.fromString(m.id()));
                if (preview == null) {
                    continue;
                }
                messages.set(i, new MessageResponse(
                    m.id(), m.chatId(), m.senderId(), m.type(), m.content(), m.replyToMsgId(),
                    m.deleted(), m.createdAt(), m.editedAt(), m.visibilityTtlSeconds(), m.attachmentFileId(),
                    m.threadId(), m.threadReplyCount(), m.mentionUserIds(), m.mentionAll(),
                    m.durationMs(), preview, m.replyPreview()));
            } catch (IllegalArgumentException ignored) {
                // skip malformed message id
            }
        }
    }
}
