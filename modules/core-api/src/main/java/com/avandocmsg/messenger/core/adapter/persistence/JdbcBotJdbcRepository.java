package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;

import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.api.bots.BotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcBotJdbcRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcBotJdbcRepository.class);
    private final DataSource dataSource;

    public JdbcBotJdbcRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean createBot(UUID botUserId, UUID ownerId, UUID orgId, String botName, String displayName, // NOSONAR java:S107 - mirrors BotRepository.createBot arity
                             String tokenHash, String listenMode, String defaultWebhookUrl) { // NOSONAR java:S1141
        var sqlUser = """
            INSERT INTO users (id, username, display_name, is_bot, created_at, updated_at)
            VALUES (?, ?, ?, true, now(), now())
            """;
        var sqlBot = """
            INSERT INTO bots (id, owner_id, org_id, bot_name, access_token_hash, listen_mode, default_webhook_url)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        var bind = new CreateBotBind(botUserId, ownerId, orgId, botName, displayName, tokenHash, listenMode, defaultWebhookUrl);
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareWrite(conn);
            JdbcConnectionSupport.callInTransaction(conn, () -> {
                insertBotUserAndRow(conn, sqlUser, sqlBot, bind);
                return true;
            });
            return true;
        } catch (Exception e) {
            log.warn("createBot failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    private record CreateBotBind(
        UUID botUserId,
        UUID ownerId,
        UUID orgId,
        String botName,
        String displayName,
        String tokenHash,
        String listenMode,
        String defaultWebhookUrl
    ) {}

    private static void insertBotUserAndRow(
        java.sql.Connection conn,
        String sqlUser,
        String sqlBot,
        CreateBotBind bind
    ) throws java.sql.SQLException {
        try (var userStmt = conn.prepareStatement(sqlUser);
             var botStmt = conn.prepareStatement(sqlBot)) {
            JdbcQuerySupport.applyDefaultTimeout(userStmt);
            JdbcQuerySupport.applyDefaultTimeout(botStmt);
            userStmt.setObject(1, bind.botUserId());
            userStmt.setString(2, bind.botName());
            userStmt.setString(3, bind.displayName());
            userStmt.executeUpdate();

            botStmt.setObject(1, bind.botUserId());
            botStmt.setObject(2, bind.ownerId());
            if (bind.orgId() != null) {
                botStmt.setObject(3, bind.orgId());
            } else {
                botStmt.setObject(3, null);
            }
            botStmt.setString(4, bind.botName());
            botStmt.setString(5, bind.tokenHash());
            botStmt.setString(6, bind.listenMode());
            botStmt.setString(7, bind.defaultWebhookUrl());
            botStmt.executeUpdate();
        }
    }

    public Optional<BotRepository.BotRow> findById(UUID botId) {
        var sql = """
            SELECT b.id, b.owner_id, b.org_id, b.bot_name, b.listen_mode, b.default_webhook_url, b.created_at, u.display_name
            FROM bots b
            JOIN users u ON u.id = b.id
            WHERE b.id = ?
            """;
        return queryOne(sql, botId);
    }

    public Optional<BotRepository.BotRow> findByTokenHash(String tokenHash) {
        var sql = """
            SELECT b.id, b.owner_id, b.org_id, b.bot_name, b.listen_mode, b.default_webhook_url, b.created_at, u.display_name
            FROM bots b
            JOIN users u ON u.id = b.id
            WHERE b.access_token_hash = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, tokenHash);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (Exception e) {
            log.warn("findByTokenHash failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    public Optional<BotRepository.BotRow> findByBotName(String botName) {
        var sql = """
            SELECT b.id, b.owner_id, b.org_id, b.bot_name, b.listen_mode, b.default_webhook_url, b.created_at, u.display_name
            FROM bots b
            JOIN users u ON u.id = b.id
            WHERE b.bot_name = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, botName);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (Exception e) {
            log.warn("findByBotName failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    public List<BotRepository.BotRow> listByOwner(UUID ownerId) {
        var sql = """
            SELECT b.id, b.owner_id, b.org_id, b.bot_name, b.listen_mode, b.default_webhook_url, b.created_at, u.display_name
            FROM bots b
            JOIN users u ON u.id = b.id
            WHERE b.owner_id = ?
            ORDER BY b.created_at DESC
            """;
        var out = new ArrayList<BotRepository.BotRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, ownerId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.warn("listByOwner failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return out;
    }

    public boolean updateDefaultWebhook(UUID botId, UUID ownerId, String webhookUrl) {
        var sql = """
            UPDATE bots SET default_webhook_url = ?
            WHERE id = ? AND owner_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, webhookUrl);
            stmt.setObject(2, botId);
            stmt.setObject(3, ownerId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("updateDefaultWebhook failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    public boolean upsertSubscription(UUID botId, UUID chatId, String webhookUrl) { // NOSONAR java:S1141 — txn helper + statement batch
        var deleteLegacy = """
            DELETE FROM bot_webhook_subscriptions
            WHERE chat_id = ? AND bot_id IS NULL
            """;
        var upsert = """
            INSERT INTO bot_webhook_subscriptions (chat_id, webhook_url, bot_id)
            VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
            """;
        var update = """
            UPDATE bot_webhook_subscriptions
            SET webhook_url = ?
            WHERE bot_id = ? AND chat_id = ?
            """;
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareWrite(conn);
            JdbcConnectionSupport.callInTransaction(conn, () -> {
                runUpsertSubscription(conn, deleteLegacy, upsert, update, botId, chatId, webhookUrl);
                return true;
            });
            return true;
        } catch (Exception e) {
            log.warn("upsertSubscription failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    private static void runUpsertSubscription(
        java.sql.Connection conn,
        String deleteLegacy,
        String upsert,
        String update,
        UUID botId,
        UUID chatId,
        String webhookUrl
    ) throws java.sql.SQLException {
        try (var del = conn.prepareStatement(deleteLegacy);
             var ins = conn.prepareStatement(upsert);
             var upd = conn.prepareStatement(update)) {
            JdbcQuerySupport.applyDefaultTimeout(del);
            JdbcQuerySupport.applyDefaultTimeout(ins);
            JdbcQuerySupport.applyDefaultTimeout(upd);
            del.setObject(1, chatId);
            del.executeUpdate();

            upd.setString(1, webhookUrl);
            upd.setObject(2, botId);
            upd.setObject(3, chatId);
            var updated = upd.executeUpdate();
            if (updated == 0) {
                ins.setObject(1, chatId);
                ins.setString(2, webhookUrl);
                ins.setObject(3, botId);
                ins.executeUpdate();
            }
        }
    }

    public boolean deleteSubscription(UUID botId, UUID chatId) {
        var sql = "DELETE FROM bot_webhook_subscriptions WHERE bot_id = ? AND chat_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, botId);
            stmt.setObject(2, chatId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("deleteSubscription failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    public List<BotRepository.ChatSubscriptionRow> listSubscriptionsForChat(UUID chatId) {
        var sql = """
            SELECT s.webhook_url, b.bot_name, b.listen_mode, b.default_webhook_url
            FROM bot_webhook_subscriptions s
            JOIN bots b ON b.id = s.bot_id
            WHERE s.chat_id = ? AND s.bot_id IS NOT NULL
            """;
        var out = new ArrayList<BotRepository.ChatSubscriptionRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new BotRepository.ChatSubscriptionRow(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4)));
                }
            }
        } catch (Exception e) {
            log.warn("listSubscriptionsForChat failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return out;
    }

    public boolean updateTokenHash(UUID botId, UUID ownerId, String tokenHash) {
        var sql = """
            UPDATE bots SET access_token_hash = ?, token_rotated_at = now()
            WHERE id = ? AND owner_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, tokenHash);
            stmt.setObject(2, botId);
            stmt.setObject(3, ownerId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("updateTokenHash failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    public void insertUpdate(UUID botId, String eventType, String payloadJson) {
        var sql = """
            INSERT INTO bot_updates (bot_id, event_type, payload)
            VALUES (?, ?, ?::jsonb)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, botId);
            stmt.setString(2, eventType);
            stmt.setString(3, payloadJson);
            stmt.executeUpdate();
        } catch (Exception e) {
            log.warn("insertUpdate failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    public List<BotRepository.BotUpdateRow> pollUpdates(UUID botId, long offset, int limit) {
        var sql = """
            SELECT id, event_type, payload::text
            FROM bot_updates
            WHERE bot_id = ? AND id > ?
            ORDER BY id ASC
            LIMIT ?
            """;
        var out = new ArrayList<BotRepository.BotUpdateRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, botId);
            stmt.setLong(2, offset);
            stmt.setInt(3, limit);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new BotRepository.BotUpdateRow(rs.getLong(1), rs.getString(2), rs.getString(3)));
                }
            }
        } catch (Exception e) {
            log.warn("pollUpdates failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return out;
    }

    public Optional<UUID> findBotIdForSubscription(UUID chatId, String botName) {
        var sql = """
            SELECT b.id FROM bot_webhook_subscriptions s
            JOIN bots b ON b.id = s.bot_id
            WHERE s.chat_id = ? AND b.bot_name = ?
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, chatId);
            stmt.setString(2, botName);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of((UUID) rs.getObject(1));
                }
            }
        } catch (Exception e) {
            log.warn("findBotIdForSubscription failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return Optional.empty();
    }

    private Optional<BotRepository.BotRow> queryOne(String sql, UUID id) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (Exception e) {
            log.warn("queryOne failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    private static BotRepository.BotRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        var created = rs.getTimestamp("created_at");
        Instant instant = created != null ? created.toInstant() : Instant.EPOCH;
        var orgObj = rs.getObject("org_id");
        UUID orgId = orgObj != null ? (UUID) orgObj : null;
        return new BotRepository.BotRow(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("owner_id"),
            orgId,
            rs.getString("bot_name"),
            rs.getString("listen_mode"),
            rs.getString("default_webhook_url"),
            instant,
            rs.getString("display_name"));
    }
}
