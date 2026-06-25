package com.avandocmsg.messenger.worker.botdelivery;

import com.avandocmsg.messenger.common.jdbc.HikariDataSources;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persisted bot webhook retries (spec 019 US7). */
final class BotWebhookOutbox {
    static final int MAX_ATTEMPTS = 5;
    static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    static final int DEFAULT_FAILED_RETENTION_DAYS = 7;
    static final int DEFAULT_FAILED_PURGE_BATCH = 500;

    private final DataSource dataSource;
    private final Clock clock;

    BotWebhookOutbox(DataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    record PendingDelivery(
        UUID id,
        UUID botId,
        UUID chatId,
        String eventId,
        String webhookUrl,
        String payloadJson,
        int attempts
    ) {
    }

    static Duration backoffForAttempt(int attemptsAfterFailure) {
        var exponent = Math.min(attemptsAfterFailure, 8);
        return BASE_BACKOFF.multipliedBy(1L << Math.max(0, exponent - 1));
    }

    static Instant nextRetryAt(Clock clock, int attemptsAfterFailure) {
        return clock.instant().plus(backoffForAttempt(attemptsAfterFailure));
    }

    void enqueue(UUID botId, UUID chatId, String eventId, String webhookUrl, String payloadJson) throws SQLException {
        var id = UUID.randomUUID();
        var sql = """
            INSERT INTO bot_webhook_outbox (id, bot_id, chat_id, event_id, webhook_url, payload_json, attempts, next_retry_at, status)
            VALUES (?, ?, ?, ?, ?, ?, 0, ?, 'pending')
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, botId);
            stmt.setObject(3, chatId);
            stmt.setString(4, eventId);
            stmt.setString(5, webhookUrl);
            stmt.setString(6, payloadJson);
            stmt.setTimestamp(7, Timestamp.from(clock.instant()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            if (!isDuplicateKey(e)) {
                throw e;
            }
        }
    }

    private static boolean isDuplicateKey(SQLException e) {
        var state = e.getSQLState();
        return state != null && state.startsWith("23");
    }

    List<PendingDelivery> fetchDue(int limit) throws SQLException {
        var sql = """
            SELECT id, bot_id, chat_id, event_id, webhook_url, payload_json, attempts
            FROM bot_webhook_outbox
            WHERE status = 'pending' AND next_retry_at <= ?
            ORDER BY next_retry_at
            LIMIT ?
            """;
        var rows = new ArrayList<PendingDelivery>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(clock.instant()));
            stmt.setInt(2, Math.max(1, limit));
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new PendingDelivery(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("bot_id"),
                        (UUID) rs.getObject("chat_id"),
                        rs.getString("event_id"),
                        rs.getString("webhook_url"),
                        rs.getString("payload_json"),
                        rs.getInt("attempts")));
                }
            }
        }
        return rows;
    }

    void markDelivered(UUID id) throws SQLException {
        var sql = """
            UPDATE bot_webhook_outbox
            SET status = 'delivered', updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.executeUpdate();
        }
    }

    Optional<PendingDelivery> scheduleRetry(UUID id, int currentAttempts) throws SQLException {
        var nextAttempts = currentAttempts + 1;
        if (nextAttempts >= MAX_ATTEMPTS) {
            markFailed(id);
            return Optional.empty();
        }
        var nextAt = nextRetryAt(clock, nextAttempts);
        var updateSql = """
            UPDATE bot_webhook_outbox
            SET attempts = ?, next_retry_at = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(updateSql)) {
            stmt.setInt(1, nextAttempts);
            stmt.setTimestamp(2, Timestamp.from(nextAt));
            stmt.setObject(3, id);
            if (stmt.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        var selectSql = """
            SELECT id, bot_id, chat_id, event_id, webhook_url, payload_json, attempts
            FROM bot_webhook_outbox WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(selectSql)) {
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new PendingDelivery(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("bot_id"),
                        (UUID) rs.getObject("chat_id"),
                        rs.getString("event_id"),
                        rs.getString("webhook_url"),
                        rs.getString("payload_json"),
                        rs.getInt("attempts")));
                }
            }
        }
        return Optional.empty();
    }

    void markFailed(UUID id) throws SQLException {
        var sql = """
            UPDATE bot_webhook_outbox
            SET status = 'failed', updated_at = ?
            WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(clock.instant()));
            stmt.setObject(2, id);
            stmt.executeUpdate();
        }
    }

    /**
     * Deletes {@code status='failed'} rows older than {@code retentionDays} (by {@code updated_at}).
     * Env: {@code BOT_WEBHOOK_OUTBOX_FAILED_RETENTION_DAYS} (default 7, 0 disables),
     * {@code BOT_WEBHOOK_OUTBOX_FAILED_PURGE_BATCH} (default 500).
     */
    int purgeFailed(int retentionDays, int batchLimit) throws SQLException {
        if (retentionDays <= 0 || batchLimit <= 0) {
            return 0;
        }
        var cutoff = Timestamp.from(clock.instant().minus(Duration.ofDays(retentionDays)));
        var sql = """
            DELETE FROM bot_webhook_outbox
            WHERE id IN (
                SELECT id FROM bot_webhook_outbox
                WHERE status = 'failed' AND updated_at < ?
                ORDER BY updated_at
                LIMIT ?
            )
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, cutoff);
            stmt.setInt(2, Math.max(1, batchLimit));
            return stmt.executeUpdate();
        }
    }

    static int failedRetentionDaysFromEnv() {
        var raw = System.getenv("BOT_WEBHOOK_OUTBOX_FAILED_RETENTION_DAYS");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_FAILED_RETENTION_DAYS;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_FAILED_RETENTION_DAYS;
        }
    }

    static int failedPurgeBatchFromEnv() {
        var raw = System.getenv("BOT_WEBHOOK_OUTBOX_FAILED_PURGE_BATCH");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_FAILED_PURGE_BATCH;
        }
        try {
            return Math.max(1, Math.min(5000, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return DEFAULT_FAILED_PURGE_BATCH;
        }
    }

    static boolean tablePresent(DataSource dataSource) {
        try (var conn = dataSource.getConnection()) {
            var md = conn.getMetaData();
            try (var rs = md.getTables(conn.getSchema(), null, "bot_webhook_outbox", new String[]{"TABLE"})) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    static DataSource testDataSource() {
        return HikariDataSources.createOptionalPool(
            "jdbc:h2:mem:bot_webhook_outbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
            2,
            "bot-outbox-test");
    }
}
