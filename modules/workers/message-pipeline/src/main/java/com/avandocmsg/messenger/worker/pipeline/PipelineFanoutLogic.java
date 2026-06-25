package com.avandocmsg.messenger.worker.pipeline;

import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DB lookup for message pipeline fan-out: recipients of a chat excluding sender, banned members,
 * and users with a block relationship with the sender (either direction).
 */
public final class PipelineFanoutLogic {
    private static final Logger log = LoggerFactory.getLogger(PipelineFanoutLogic.class);

    /** Aligns with {@code JdbcListLimits.CHAT_MEMBERS} (core-api); worker has no core-api dep. */
    private static final int MAX_CHAT_MEMBERS = 10_000;

    private PipelineFanoutLogic() {
    }

    /**
     * Members of {@code chatId} eligible for delivery (not {@code excludeSenderId}, not banned,
     * not blocked with {@code excludeSenderId} in either direction).
     */
    public static List<String> loadRecipientUserIds(DataSource dataSource, UUID chatId, UUID excludeSenderId,
                                                    UserMessageSource workerMessages) {
        var sql = """
            SELECT cm.user_id FROM chat_members cm
            WHERE cm.chat_id = ? AND cm.user_id != ? AND cm.banned = false
              AND NOT EXISTS (
                SELECT 1 FROM blocks b
                WHERE (b.blocker_id = ? AND b.blocked_id = cm.user_id)
                   OR (b.blocker_id = cm.user_id AND b.blocked_id = ?)
              )
            LIMIT ?
            """;
        var result = new ArrayList<String>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, excludeSenderId);
            stmt.setObject(3, excludeSenderId);
            stmt.setObject(4, excludeSenderId);
            stmt.setInt(5, MAX_CHAT_MEMBERS);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getObject("user_id", UUID.class).toString());
                }
            }
        } catch (Exception e) {
            log.error(workerMessages.format("worker.common.members_load_failed", chatId), e);
        }
        return result;
    }

    public static List<String> loadAllChatMemberUserIds(DataSource dataSource, UUID chatId,
                                                        UserMessageSource workerMessages) {
        var sql = """
            SELECT cm.user_id FROM chat_members cm
            WHERE cm.chat_id = ? AND cm.banned = false
            """;
        var result = new ArrayList<String>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getObject("user_id", UUID.class).toString());
                }
            }
        } catch (Exception e) {
            log.error(workerMessages.format("worker.common.members_list_failed", chatId), e);
        }
        return result;
    }

    /**
     * Scoped presence recipients: contacts + co-chat members (spec 025 FR-013).
     * Replaces org-wide fan-out for {@code user.presence}.
     */
    public static List<String> loadPresenceRecipientUserIds(DataSource dataSource, UUID userId,
                                                            UserMessageSource workerMessages) {
        var sql = """
            SELECT DISTINCT peer FROM (
              SELECT contact_user_id AS peer FROM contacts WHERE user_id = ?
              UNION
              SELECT user_id AS peer FROM contacts WHERE contact_user_id = ?
              UNION
              SELECT cm2.user_id AS peer FROM chat_members cm1
              INNER JOIN chat_members cm2 ON cm1.chat_id = cm2.chat_id
              WHERE cm1.user_id = ? AND cm2.user_id != ? AND cm1.banned = false AND cm2.banned = false
            ) scoped
            WHERE peer != ?
            """;
        var result = new ArrayList<String>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, userId);
            stmt.setObject(3, userId);
            stmt.setObject(4, userId);
            stmt.setObject(5, userId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getObject("peer", UUID.class).toString());
                }
            }
        } catch (Exception e) {
            log.error("presence recipients load failed user={}", userId, e);
        }
        return result;
    }

    /** Active users in org (legacy; prefer {@link #loadPresenceRecipientUserIds}). */
    public static List<String> loadOrgUserIds(DataSource dataSource, UUID orgId, UUID excludeUserId,
                                              UserMessageSource workerMessages) {
        var sql = """
            SELECT id FROM users
            WHERE org_id = ? AND hidden = false AND id != ?
            """;
        var result = new ArrayList<String>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            stmt.setObject(2, excludeUserId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getObject("id", UUID.class).toString());
                }
            }
        } catch (Exception e) {
            log.error("org users load failed org={}", orgId, e);
        }
        return result;
    }

    public static boolean isChatMember(DataSource dataSource, UUID chatId, UUID userId,
                                       UserMessageSource workerMessages) {
        var sql = """
            SELECT 1 FROM chat_members
            WHERE chat_id = ? AND user_id = ? AND banned = false
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, userId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error(workerMessages.format("worker.common.membership_check_failed", userId, chatId), e);
            return false;
        }
    }
}
