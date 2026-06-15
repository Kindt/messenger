package com.avandocmsg.messenger.api.bots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BotRepository {
    private static final Logger log = LoggerFactory.getLogger(BotRepository.class);
    private final DataSource dataSource;

    public BotRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record BotRow(
        UUID id,
        UUID ownerId,
        UUID orgId,
        String botName,
        String listenMode,
        String defaultWebhookUrl,
        Instant createdAt,
        String displayName
    ) {}

    public record ChatSubscriptionRow(
        String webhookUrl,
        String botName,
        String listenMode,
        String defaultWebhookUrl
    ) {}

    public boolean createBot(UUID botUserId, UUID ownerId, UUID orgId, String botName, String displayName,
                             String tokenHash, String listenMode, String defaultWebhookUrl) {
        var sqlUser = """
            INSERT INTO users (id, username, display_name, is_bot, created_at, updated_at)
            VALUES (?, ?, ?, true, now(), now())
            """;
        var sqlBot = """
            INSERT INTO bots (id, owner_id, org_id, bot_name, access_token_hash, listen_mode, default_webhook_url)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var userStmt = conn.prepareStatement(sqlUser);
                 var botStmt = conn.prepareStatement(sqlBot)) {
                userStmt.setObject(1, botUserId);
                userStmt.setString(2, botName);
                userStmt.setString(3, displayName);
                userStmt.executeUpdate();

                botStmt.setObject(1, botUserId);
                botStmt.setObject(2, ownerId);
                if (orgId != null) {
                    botStmt.setObject(3, orgId);
                } else {
                    botStmt.setObject(3, null);
                }
                botStmt.setString(4, botName);
                botStmt.setString(5, tokenHash);
                botStmt.setString(6, listenMode);
                botStmt.setString(7, defaultWebhookUrl);
                botStmt.executeUpdate();
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            log.warn("createBot failed: {}", e.getMessage());
            return false;
        }
    }

    public Optional<BotRow> findById(UUID botId) {
        var sql = """
            SELECT b.id, b.owner_id, b.org_id, b.bot_name, b.listen_mode, b.default_webhook_url, b.created_at, u.display_name
            FROM bots b
            JOIN users u ON u.id = b.id
            WHERE b.id = ?
            """;
        return queryOne(sql, botId);
    }

    public Optional<BotRow> findByTokenHash(String tokenHash) {
        var sql = """
            SELECT b.id, b.owner_id, b.org_id, b.bot_name, b.listen_mode, b.default_webhook_url, b.created_at, u.display_name
            FROM bots b
            JOIN users u ON u.id = b.id
            WHERE b.access_token_hash = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tokenHash);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (Exception e) {
            log.warn("findByTokenHash failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<BotRow> findByBotName(String botName) {
        var sql = """
            SELECT b.id, b.owner_id, b.org_id, b.bot_name, b.listen_mode, b.default_webhook_url, b.created_at, u.display_name
            FROM bots b
            JOIN users u ON u.id = b.id
            WHERE b.bot_name = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, botName);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (Exception e) {
            log.warn("findByBotName failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<BotRow> listByOwner(UUID ownerId) {
        var sql = """
            SELECT b.id, b.owner_id, b.org_id, b.bot_name, b.listen_mode, b.default_webhook_url, b.created_at, u.display_name
            FROM bots b
            JOIN users u ON u.id = b.id
            WHERE b.owner_id = ?
            ORDER BY b.created_at DESC
            """;
        var out = new ArrayList<BotRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, ownerId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.warn("listByOwner failed: {}", e.getMessage());
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
            stmt.setString(1, webhookUrl);
            stmt.setObject(2, botId);
            stmt.setObject(3, ownerId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("updateDefaultWebhook failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean upsertSubscription(UUID botId, UUID chatId, String webhookUrl) {
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
            conn.setAutoCommit(false);
            try (var del = conn.prepareStatement(deleteLegacy);
                 var ins = conn.prepareStatement(upsert);
                 var upd = conn.prepareStatement(update)) {
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
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            log.warn("upsertSubscription failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean deleteSubscription(UUID botId, UUID chatId) {
        var sql = "DELETE FROM bot_webhook_subscriptions WHERE bot_id = ? AND chat_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, botId);
            stmt.setObject(2, chatId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("deleteSubscription failed: {}", e.getMessage());
            return false;
        }
    }

    public List<ChatSubscriptionRow> listSubscriptionsForChat(UUID chatId) {
        var sql = """
            SELECT s.webhook_url, b.bot_name, b.listen_mode, b.default_webhook_url
            FROM bot_webhook_subscriptions s
            JOIN bots b ON b.id = s.bot_id
            WHERE s.chat_id = ? AND s.bot_id IS NOT NULL
            """;
        var out = new ArrayList<ChatSubscriptionRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new ChatSubscriptionRow(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4)));
                }
            }
        } catch (Exception e) {
            log.warn("listSubscriptionsForChat failed: {}", e.getMessage());
        }
        return out;
    }

    public record BotUpdateRow(long id, String eventType, String payloadJson) {}

    public boolean updateTokenHash(UUID botId, UUID ownerId, String tokenHash) {
        var sql = """
            UPDATE bots SET access_token_hash = ?, token_rotated_at = now()
            WHERE id = ? AND owner_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tokenHash);
            stmt.setObject(2, botId);
            stmt.setObject(3, ownerId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("updateTokenHash failed: {}", e.getMessage());
            return false;
        }
    }

    public void insertUpdate(UUID botId, String eventType, String payloadJson) {
        var sql = """
            INSERT INTO bot_updates (bot_id, event_type, payload)
            VALUES (?, ?, ?::jsonb)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, botId);
            stmt.setString(2, eventType);
            stmt.setString(3, payloadJson);
            stmt.executeUpdate();
        } catch (Exception e) {
            log.warn("insertUpdate failed: {}", e.getMessage());
        }
    }

    public List<BotUpdateRow> pollUpdates(UUID botId, long offset, int limit) {
        var sql = """
            SELECT id, event_type, payload::text
            FROM bot_updates
            WHERE bot_id = ? AND id > ?
            ORDER BY id ASC
            LIMIT ?
            """;
        var out = new ArrayList<BotUpdateRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, botId);
            stmt.setLong(2, offset);
            stmt.setInt(3, limit);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new BotUpdateRow(rs.getLong(1), rs.getString(2), rs.getString(3)));
                }
            }
        } catch (Exception e) {
            log.warn("pollUpdates failed: {}", e.getMessage());
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
            stmt.setObject(1, chatId);
            stmt.setString(2, botName);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of((UUID) rs.getObject(1));
                }
            }
        } catch (Exception e) {
            log.warn("findBotIdForSubscription failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<BotRow> queryOne(String sql, UUID id) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (Exception e) {
            log.warn("queryOne failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static BotRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        var created = rs.getTimestamp("created_at");
        Instant instant = created != null ? created.toInstant() : Instant.EPOCH;
        var orgObj = rs.getObject("org_id");
        UUID orgId = orgObj != null ? (UUID) orgObj : null;
        return new BotRow(
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
