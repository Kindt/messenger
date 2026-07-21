package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;

import java.sql.ResultSet;

/** Maps JDBC rows to {@link MessageResponse} (hex adapter). */
final class MessageResponseJdbcMapper {
    private MessageResponseJdbcMapper() {
    }

    static MessageResponse mapMessage(ResultSet rs) throws java.sql.SQLException {
        var ts = rs.getTimestamp("created_at");
        var editedTs = rs.getTimestamp("edited_at");
        var replyTo = rs.getObject("reply_to_msg_id", java.util.UUID.class);
        var threadId = hasColumn(rs, "thread_id") ? rs.getObject("thread_id", java.util.UUID.class) : null;
        var ttl = (Integer) rs.getObject("visibility_ttl_seconds");
        var attachmentFileId = rs.getObject("attachment_file_id", java.util.UUID.class);
        Integer threadReplyCount = null;
        if (hasColumn(rs, "thread_reply_count")) {
            var count = rs.getObject("thread_reply_count");
            if (count instanceof Number n) {
                threadReplyCount = n.intValue();
            }
        }
        Integer durationMs = null;
        if (hasColumn(rs, "voice_duration_ms")) {
            durationMs = (Integer) rs.getObject("voice_duration_ms");
        }
        return new MessageResponse(
            rs.getObject("id", java.util.UUID.class).toString(),
            rs.getObject("chat_id", java.util.UUID.class).toString(),
            rs.getObject("sender_id", java.util.UUID.class).toString(),
            rs.getString("type"),
            rs.getString("content"),
            replyTo != null ? replyTo.toString() : null,
            rs.getBoolean("deleted"),
            ts != null ? ts.toInstant() : null,
            editedTs != null ? editedTs.toInstant() : null,
            ttl,
            attachmentFileId != null ? attachmentFileId.toString() : null,
            threadId != null ? threadId.toString() : null,
            threadReplyCount,
            null,
            null,
            durationMs,
            null,
            mapReplyPreview(rs));
    }

    static com.avandocmsg.messenger.api.messages.dto.MessageReplyPreview mapReplyPreview(ResultSet rs)
        throws java.sql.SQLException {
        if (!hasColumn(rs, "reply_preview_id")) {
            return null;
        }
        var previewId = rs.getObject("reply_preview_id", java.util.UUID.class);
        if (previewId == null) {
            return null;
        }
        var deleted = rs.getBoolean("reply_preview_deleted");
        String snippet = null;
        if (!deleted) {
            var content = hasColumn(rs, "reply_preview_content") ? rs.getString("reply_preview_content") : null;
            if (content != null && !content.isBlank()) {
                snippet = content.length() > 120 ? content.substring(0, 120) : content;
            }
        }
        return new com.avandocmsg.messenger.api.messages.dto.MessageReplyPreview(
            previewId.toString(),
            rs.getObject("reply_preview_sender_id", java.util.UUID.class).toString(),
            snippet,
            deleted);
    }

    static boolean hasColumn(ResultSet rs, String column) throws java.sql.SQLException {
        var meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (column.equalsIgnoreCase(meta.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }
}
