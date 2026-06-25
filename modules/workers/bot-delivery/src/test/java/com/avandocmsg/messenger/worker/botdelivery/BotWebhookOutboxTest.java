package com.avandocmsg.messenger.worker.botdelivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotWebhookOutboxTest {

    private static final UUID CHAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID BOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final String EVENT_ID = "msg-outbox-1";
    private static final String WEBHOOK = "https://example.test/hook";
    private static final String PAYLOAD = "{\"event_id\":\"msg-outbox-1\"}";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-06-18T12:00:00Z"));
    private BotWebhookOutbox outbox;

    @BeforeEach
    void setUp() throws Exception {
        var ds = BotWebhookOutbox.testDataSource();
        try (var conn = ds.getConnection(); var st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS bot_webhook_outbox (
                    id UUID PRIMARY KEY,
                    bot_id UUID,
                    chat_id UUID NOT NULL,
                    event_id VARCHAR(128) NOT NULL,
                    webhook_url VARCHAR(2048) NOT NULL,
                    payload_json CLOB NOT NULL,
                    attempts INT NOT NULL DEFAULT 0,
                    next_retry_at TIMESTAMP NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'pending',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_bot_webhook_outbox_dedup ON bot_webhook_outbox (event_id, webhook_url)");
            st.execute("DELETE FROM bot_webhook_outbox");
        }
        outbox = new BotWebhookOutbox(ds, clock);
    }

    @Test
    void enqueue_and_fetchDue_returnsPendingRow() throws Exception {
        outbox.enqueue(BOT_ID, CHAT_ID, EVENT_ID, WEBHOOK, PAYLOAD);

        var due = outbox.fetchDue(10);
        assertEquals(1, due.size());
        assertEquals(EVENT_ID, due.getFirst().eventId());
        assertEquals(WEBHOOK, due.getFirst().webhookUrl());
        assertEquals(0, due.getFirst().attempts());
    }

    @Test
    void scheduleRetry_incrementsAttemptsWithExponentialBackoff() throws Exception {
        outbox.enqueue(BOT_ID, CHAT_ID, EVENT_ID, WEBHOOK, PAYLOAD);
        var row = outbox.fetchDue(1).getFirst();

        outbox.scheduleRetry(row.id(), row.attempts());
        clock.advanceSeconds(29);
        assertTrue(outbox.fetchDue(10).isEmpty());

        clock.advanceSeconds(2);
        var retry = outbox.fetchDue(10);
        assertEquals(1, retry.size());
        assertEquals(1, retry.getFirst().attempts());
    }

    @Test
    void scheduleRetry_marksFailedAfterMaxAttempts() throws Exception {
        outbox.enqueue(BOT_ID, CHAT_ID, EVENT_ID, WEBHOOK, PAYLOAD);
        var id = outbox.fetchDue(1).getFirst().id();

        for (int i = 0; i < BotWebhookOutbox.MAX_ATTEMPTS - 1; i++) {
            outbox.scheduleRetry(id, i);
            clock.advance(BotWebhookOutbox.backoffForAttempt(i + 1).plusSeconds(1));
        }
        outbox.scheduleRetry(id, BotWebhookOutbox.MAX_ATTEMPTS - 1);

        assertTrue(outbox.fetchDue(10).isEmpty());
    }

    @Test
    void markDelivered_removesFromDueQueue() throws Exception {
        outbox.enqueue(BOT_ID, CHAT_ID, EVENT_ID, WEBHOOK, PAYLOAD);
        var id = outbox.fetchDue(1).getFirst().id();
        outbox.markDelivered(id);
        assertTrue(outbox.fetchDue(10).isEmpty());
    }

    @Test
    void backoffForAttempt_doublesFromBase() {
        assertEquals(30, BotWebhookOutbox.backoffForAttempt(1).toSeconds());
        assertEquals(60, BotWebhookOutbox.backoffForAttempt(2).toSeconds());
        assertEquals(120, BotWebhookOutbox.backoffForAttempt(3).toSeconds());
    }

    @Test
    void tablePresent_falseWhenMissing() throws Exception {
        var ds = BotWebhookOutbox.testDataSource();
        try (var conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS bot_webhook_outbox");
        }
        assertFalse(BotWebhookOutbox.tablePresent(ds));
    }

    @Test
    void purgeFailed_deletesOldFailedRows() throws Exception {
        outbox.enqueue(BOT_ID, CHAT_ID, EVENT_ID, WEBHOOK, PAYLOAD);
        var id = outbox.fetchDue(1).getFirst().id();
        for (int i = 0; i < BotWebhookOutbox.MAX_ATTEMPTS - 1; i++) {
            outbox.scheduleRetry(id, i);
            clock.advance(BotWebhookOutbox.backoffForAttempt(i + 1).plusSeconds(1));
        }
        outbox.scheduleRetry(id, BotWebhookOutbox.MAX_ATTEMPTS - 1);

        clock.advance(java.time.Duration.ofDays(8));
        var deleted = outbox.purgeFailed(7, 100);
        assertEquals(1, deleted);
    }

    @Test
    void purgeFailed_keepsRecentFailedRows() throws Exception {
        outbox.enqueue(BOT_ID, CHAT_ID, EVENT_ID, WEBHOOK, PAYLOAD);
        var id = outbox.fetchDue(1).getFirst().id();
        outbox.markFailed(id);

        var deleted = outbox.purgeFailed(7, 100);
        assertEquals(0, deleted);
    }

    @Test
    void purgeFailed_respectsBatchLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            var eventId = EVENT_ID + "-" + i;
            outbox.enqueue(BOT_ID, CHAT_ID, eventId, WEBHOOK, PAYLOAD);
            var rowId = outbox.fetchDue(10).stream()
                .filter(r -> r.eventId().equals(eventId))
                .findFirst()
                .orElseThrow()
                .id();
            outbox.markFailed(rowId);
        }
        clock.advance(java.time.Duration.ofDays(8));

        assertEquals(2, outbox.purgeFailed(7, 2));
        assertEquals(3, outbox.purgeFailed(7, 10));
    }

    @Test
    void failedRetentionDaysFromEnv_defaultsWhenUnset() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            System.getenv("BOT_WEBHOOK_OUTBOX_FAILED_RETENTION_DAYS") == null);
        assertEquals(BotWebhookOutbox.DEFAULT_FAILED_RETENTION_DAYS, BotWebhookOutbox.failedRetentionDaysFromEnv());
    }

    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        void advance(java.time.Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
