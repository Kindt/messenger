package com.avandocmsg.messenger.worker.exportreplay;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Extracted SQL and message body helpers for export-replay. */
final class ExportMessageLoader {
    private ExportMessageLoader() {
    }

    /** Same visibility rule as MessageRepository (unqualified column names on messages). */
    static final String SQL_MSG_VISIBILITY_TTL_VISIBLE =
        "(visibility_ttl_seconds IS NULL OR EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - created_at)) < visibility_ttl_seconds)";

    /** UUIDs in plaintext message/version bodies (and paths such as /api/v1/files/<uuid>/download). */
    private static final Pattern CONTENT_FILE_UUID_PATTERN = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * MLS-encrypted payloads are stored with {@code e2ee-*} types ({@code MessageService});
     * plaintext is not exported here.
     */
    static boolean isE2eeEnvelopeType(String type) {
        return type != null && type.startsWith("e2ee-");
    }

    /**
     * Scans {@code text} for UUID-shaped tokens (e.g. embedded in URLs). Does not scan E2EE ciphertext callers should gate.
     *
     * @return true if {@code maxSinkSize} was reached while more matches may remain in the string
     */
    static boolean collectFileIdsFromText(String text, Set<UUID> sink, int maxSinkSize) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        var m = CONTENT_FILE_UUID_PATTERN.matcher(text);
        while (m.find()) {
            if (sink.size() >= maxSinkSize) {
                return true;
            }
            try {
                sink.add(UUID.fromString(m.group()));
            } catch (IllegalArgumentException ignored) {
                // non-UUID 128-bit hex match
            }
        }
        return false;
    }

    /**
     * @return true if {@code maxSinkSize} is already reached (caller should record truncation).
     */
    static boolean tryAddUuidString(String raw, Set<UUID> sink, int maxSinkSize) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        if (sink.size() >= maxSinkSize) {
            return true;
        }
        try {
            sink.add(UUID.fromString(raw));
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String messageSubsetWhere(boolean applyTtlFilter) {
        return "chat_id = ?::uuid" + (applyTtlFilter ? " AND " + SQL_MSG_VISIBILITY_TTL_VISIBLE : "");
    }

    static String buildMessageIdSubsetSql(boolean applyTtlFilter) {
        return "SELECT id FROM messages WHERE " + messageSubsetWhere(applyTtlFilter) + " ORDER BY created_at ASC LIMIT ?";
    }

    static String buildMessagesSql(boolean applyTtlFilter) {
        return """
            SELECT id, sender_id, client_msg_id, type, content, reply_to_msg_id, deleted, visibility_ttl_seconds, created_at, edited_at
            FROM messages
            WHERE %s
            ORDER BY created_at ASC
            LIMIT ?
            """.formatted(messageSubsetWhere(applyTtlFilter));
    }

    static String buildMessageVersionsSql(boolean applyTtlFilter) {
        return """
            SELECT mv.id, mv.message_id, mv.content, mv.edited_by, mv.created_at
            FROM message_versions mv
            WHERE mv.message_id IN (
                %s
            )
            ORDER BY mv.created_at ASC
            LIMIT ?
            """.formatted(buildMessageIdSubsetSql(applyTtlFilter));
    }

    static String buildMessageReactionsSql(boolean applyTtlFilter) {
        return """
            SELECT mr.message_id, mr.user_id, mr.reaction, mr.created_at
            FROM message_reactions mr
            WHERE mr.message_id IN (
                %s
            )
            ORDER BY mr.created_at ASC
            LIMIT ?
            """.formatted(buildMessageIdSubsetSql(applyTtlFilter));
    }

    static String buildPinnedMessagesSql(boolean applyTtlFilter) {
        return """
            SELECT pm.message_id, pm.pinned_by, pm.created_at
            FROM pinned_messages pm
            WHERE pm.chat_id = ?::uuid
              AND pm.message_id IN (
                %s
              )
            ORDER BY pm.created_at ASC
            LIMIT ?
            """.formatted(buildMessageIdSubsetSql(applyTtlFilter));
    }

    static String buildReferencedUsersSql(boolean applyTtlFilter) {
        var ms = "SELECT id, sender_id FROM messages WHERE " + messageSubsetWhere(applyTtlFilter)
            + " ORDER BY created_at ASC LIMIT ?";
        return """
            WITH ms AS (
                %s
            ),
            mbr AS (
                SELECT user_id AS uid FROM chat_members WHERE chat_id = ?::uuid ORDER BY joined_at ASC, user_id LIMIT ?
            ),
            fup AS (
                SELECT DISTINCT uploaded_by AS uid FROM file_metadata WHERE id = ANY(?::uuid[])
            )
            SELECT DISTINCT u.id, u.username, u.display_name, u.hidden, u.org_id, u.created_at, u.updated_at
            FROM users u
            WHERE u.id IN (SELECT sender_id FROM ms)
               OR u.id IN (SELECT uid FROM mbr)
               OR u.id IN (SELECT edited_by FROM message_versions mv WHERE mv.message_id IN (SELECT id FROM ms))
               OR u.id IN (SELECT user_id FROM message_reactions mr WHERE mr.message_id IN (SELECT id FROM ms))
               OR u.id IN (SELECT pinned_by FROM pinned_messages pm WHERE pm.chat_id = ?::uuid AND pm.message_id IN (SELECT id FROM ms))
               OR u.id IN (SELECT owner_id FROM chats WHERE id = ?::uuid AND owner_id IS NOT NULL)
               OR u.id IN (SELECT uid FROM fup)
            ORDER BY u.username ASC, u.id ASC
            LIMIT ?
            """.formatted(ms);
    }
}
