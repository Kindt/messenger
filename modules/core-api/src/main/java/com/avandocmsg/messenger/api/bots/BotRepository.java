package com.avandocmsg.messenger.api.bots;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcBotJdbcRepository;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for bot JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcBotJdbcRepository}.
 */
public class BotRepository {
    private final JdbcBotJdbcRepository jdbc;

    public BotRepository(DataSource dataSource) {
        this.jdbc = new JdbcBotJdbcRepository(dataSource);
    }

    public JdbcBotJdbcRepository jdbcRepository() {
        return jdbc;
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
        return jdbc.createBot(botUserId, ownerId, orgId, botName, displayName, tokenHash, listenMode, defaultWebhookUrl);
    }

    public Optional<BotRow> findById(UUID botId) {
        return jdbc.findById(botId);
    }

    public Optional<BotRow> findByTokenHash(String tokenHash) {
        return jdbc.findByTokenHash(tokenHash);
    }

    public Optional<BotRow> findByBotName(String botName) {
        return jdbc.findByBotName(botName);
    }

    public List<BotRow> listByOwner(UUID ownerId) {
        return jdbc.listByOwner(ownerId);
    }

    public boolean updateDefaultWebhook(UUID botId, UUID ownerId, String webhookUrl) {
        return jdbc.updateDefaultWebhook(botId, ownerId, webhookUrl);
    }

    public boolean upsertSubscription(UUID botId, UUID chatId, String webhookUrl) {
        return jdbc.upsertSubscription(botId, chatId, webhookUrl);
    }

    public boolean deleteSubscription(UUID botId, UUID chatId) {
        return jdbc.deleteSubscription(botId, chatId);
    }

    public List<ChatSubscriptionRow> listSubscriptionsForChat(UUID chatId) {
        return jdbc.listSubscriptionsForChat(chatId);
    }

    public record BotUpdateRow(long id, String eventType, String payloadJson) {}

    public boolean updateTokenHash(UUID botId, UUID ownerId, String tokenHash) {
        return jdbc.updateTokenHash(botId, ownerId, tokenHash);
    }

    public void insertUpdate(UUID botId, String eventType, String payloadJson) {
        jdbc.insertUpdate(botId, eventType, payloadJson);
    }

    public List<BotUpdateRow> pollUpdates(UUID botId, long offset, int limit) {
        return jdbc.pollUpdates(botId, offset, limit);
    }

    public Optional<UUID> findBotIdForSubscription(UUID chatId, String botName) {
        return jdbc.findBotIdForSubscription(chatId, botName);
    }
}
