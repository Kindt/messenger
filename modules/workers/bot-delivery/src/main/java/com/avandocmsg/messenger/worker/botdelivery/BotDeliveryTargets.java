package com.avandocmsg.messenger.worker.botdelivery;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Single-query webhook subscription loading with optional per-batch prefetch (spec 025 FR-127). */
public final class BotDeliveryTargets {
    static final String SUBSCRIPTIONS_TABLE = "bot_webhook_subscriptions";
    /** Upper bound for subscriptions scanned per chat (unbounded list guard). */
    public static final int MAX_SUBSCRIPTIONS_PER_CHAT = 100;

    public record SubscriptionRow(
        UUID botId,
        String subscriptionWebhookUrl,
        String botName,
        String listenMode,
        String defaultWebhookUrl
    ) {}

    public record ResolvedTarget(UUID botId, String webhookUrl) {}

    private BotDeliveryTargets() {
    }

    /**
     * Loads all webhook subscriptions for a chat in one query (JOIN bots when present).
     */
    public static List<SubscriptionRow> loadWebhookTargets(Connection conn, UUID chatId) throws SQLException {
        if (tableExists(conn, "bots")) {
            return loadWithBotsJoin(conn, chatId);
        }
        return loadLegacy(conn, chatId);
    }

    private static List<SubscriptionRow> loadWithBotsJoin(Connection conn, UUID chatId) throws SQLException {
        var sql = """
            SELECT s.webhook_url, b.bot_name, b.listen_mode, b.default_webhook_url, s.bot_id
            FROM bot_webhook_subscriptions s
            LEFT JOIN bots b ON b.id = s.bot_id
            WHERE s.chat_id = ?
            LIMIT ?
            """;
        var rows = new ArrayList<SubscriptionRow>();
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setInt(2, MAX_SUBSCRIPTIONS_PER_CHAT);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SubscriptionRow(
                        (UUID) rs.getObject("bot_id"),
                        rs.getString("webhook_url"),
                        rs.getString("bot_name"),
                        rs.getString("listen_mode"),
                        rs.getString("default_webhook_url")
                    ));
                }
            }
        }
        return rows;
    }

    private static List<SubscriptionRow> loadLegacy(Connection conn, UUID chatId) throws SQLException {
        var sql = "SELECT webhook_url FROM " + SUBSCRIPTIONS_TABLE + " WHERE chat_id = ? LIMIT ?";
        var rows = new ArrayList<SubscriptionRow>();
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setInt(2, MAX_SUBSCRIPTIONS_PER_CHAT);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SubscriptionRow(null, rs.getString(1), null, null, null));
                }
            }
        }
        return rows;
    }

    public static List<ResolvedTarget> resolveForEvent(MessageWorkerEvent event, List<SubscriptionRow> rows) {
        var targets = new ArrayList<ResolvedTarget>();
        for (var row : rows) {
            if (row.botId() == null) {
                addTarget(targets, null, row.subscriptionWebhookUrl());
                continue;
            }
            if (!BotEventFilter.shouldDeliver(event, row.botName(), row.listenMode())) {
                continue;
            }
            var effective = row.subscriptionWebhookUrl() != null && !row.subscriptionWebhookUrl().isBlank()
                ? row.subscriptionWebhookUrl()
                : row.defaultWebhookUrl();
            addTarget(targets, row.botId(), effective);
        }
        return targets;
    }

    private static void addTarget(List<ResolvedTarget> targets, UUID botId, String raw) {
        if (raw != null && !raw.isBlank()) {
            targets.add(new ResolvedTarget(botId, raw.trim()));
        }
    }

    /** One subscription query per chat while processing a batch of events. */
    public static final class Prefetch {
        private final DataSource dataSource;
        private final Map<UUID, List<SubscriptionRow>> byChat = new HashMap<>();

        public Prefetch(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public List<SubscriptionRow> forChat(UUID chatId) throws SQLException {
            var cached = byChat.get(chatId);
            if (cached != null) {
                return cached;
            }
            try (var conn = dataSource.getConnection()) {
                var rows = loadWebhookTargets(conn, chatId);
                byChat.put(chatId, rows);
                return rows;
            }
        }

        public void clear() {
            byChat.clear();
        }
    }

    static boolean tableExists(Connection conn, String table) throws SQLException {
        var md = conn.getMetaData();
        try (var rs = md.getTables(conn.getSchema(), null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    static HikariDataSource testDataSource() {
        return new HikariDataSource(new com.zaxxer.hikari.HikariConfig() {{
            setJdbcUrl("jdbc:h2:mem:bot_delivery_targets;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
            setUsername("sa");
            setPassword("");
            setMaximumPoolSize(2);
        }});
    }
}
