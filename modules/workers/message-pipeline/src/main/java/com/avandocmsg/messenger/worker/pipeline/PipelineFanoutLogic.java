package com.avandocmsg.messenger.worker.pipeline;

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

    private PipelineFanoutLogic() {
    }

    /**
     * Members of {@code chatId} eligible for delivery (not {@code excludeSenderId}, not banned,
     * not blocked with {@code excludeSenderId} in either direction).
     */
    public static List<String> loadRecipientUserIds(DataSource dataSource, UUID chatId, UUID excludeSenderId) {
        var sql = """
            SELECT cm.user_id FROM chat_members cm
            WHERE cm.chat_id = ? AND cm.user_id != ? AND cm.banned = false
              AND cm.user_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = ?)
              AND cm.user_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?)
            """;
        var result = new ArrayList<String>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, excludeSenderId);
            stmt.setObject(3, excludeSenderId);
            stmt.setObject(4, excludeSenderId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getObject("user_id", UUID.class).toString());
                }
            }
        } catch (Exception e) {
            log.error("Failed to get chat members for {}", chatId, e);
        }
        return result;
    }
}
