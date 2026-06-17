package com.avandocmsg.messenger.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Loads chat memberships for WS broadcast routing (PS-1.3). */
public final class WsChatMembershipLoader {

    private static final Logger log = LoggerFactory.getLogger(WsChatMembershipLoader.class);

    private final DataSource dataSource;

    public WsChatMembershipLoader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<String> loadChatIds(UUID userId) {
        if (dataSource == null || userId == null) {
            return List.of();
        }
        var sql = """
            SELECT chat_id FROM chat_members
            WHERE user_id = ? AND banned = false
            """;
        var result = new ArrayList<String>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getObject("chat_id", UUID.class).toString());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load chat memberships for user {}: {}", userId, e.getMessage());
        }
        return result;
    }
}
