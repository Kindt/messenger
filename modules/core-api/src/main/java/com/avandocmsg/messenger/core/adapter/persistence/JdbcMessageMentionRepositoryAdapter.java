package com.avandocmsg.messenger.core.adapter.persistence;


import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.port.MessageMentionRepositoryPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JDBC adapter for {@link MessageMentionRepositoryPort}. */
public final class JdbcMessageMentionRepositoryAdapter implements MessageMentionRepositoryPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcMessageMentionRepositoryAdapter.class);

    private final DataSource dataSource;

    public JdbcMessageMentionRepositoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insertMentions(UUID messageId, List<MentionRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        var sql = "INSERT INTO message_mentions (message_id, user_id, mention_kind) VALUES (?, ?, ?)";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, messageId);
            for (var row : rows) {
                stmt.setObject(2, row.userId());
                stmt.setString(3, row.kind());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (Exception e) {
            log.error("insertMentions failed for {}", messageId, e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    @Override
    public Map<UUID, MentionSummary> findSummariesByMessageIds(List<UUID> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        var sql = new StringBuilder(
            "SELECT message_id, user_id, mention_kind FROM message_mentions WHERE message_id IN (");
        for (int i = 0; i < messageIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(")");
        var userIdsByMessage = new HashMap<UUID, List<String>>();
        var mentionAll = new HashMap<UUID, Boolean>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql.toString())) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            int idx = 1;
            for (var id : messageIds) {
                stmt.setObject(idx++, id);
            }
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var msgId = rs.getObject("message_id", UUID.class);
                    var userId = rs.getObject("user_id", UUID.class);
                    var kind = rs.getString("mention_kind");
                    if ("all".equalsIgnoreCase(kind)) {
                        mentionAll.put(msgId, true);
                    }
                    userIdsByMessage.computeIfAbsent(msgId, k -> new ArrayList<>())
                        .add(userId.toString());
                }
            }
        } catch (Exception e) {
            log.error("findSummariesByMessageIds failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
        var out = new HashMap<UUID, MentionSummary>();
        for (var id : messageIds) {
            var users = userIdsByMessage.getOrDefault(id, List.of());
            out.put(id, new MentionSummary(users, mentionAll.getOrDefault(id, false)));
        }
        return out;
    }

    @Override
    public boolean isUserMentioned(UUID messageId, UUID userId) {
        var sql = "SELECT 1 FROM message_mentions WHERE message_id = ? AND user_id = ? LIMIT 1";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, messageId);
            stmt.setObject(2, userId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("isUserMentioned failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    @Override
    public boolean hasMentionAll(UUID messageId) {
        var sql = "SELECT 1 FROM message_mentions WHERE message_id = ? AND mention_kind = 'all' LIMIT 1";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, messageId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("hasMentionAll failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }
}
