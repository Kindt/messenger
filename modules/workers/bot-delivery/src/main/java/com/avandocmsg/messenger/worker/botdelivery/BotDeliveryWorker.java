package com.avandocmsg.messenger.worker.botdelivery;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.health.WorkerDependencyHealth;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.avandocmsg.messenger.common.http.HttpClientSupport;
import com.avandocmsg.messenger.common.jdbc.HikariDataSources;
import com.avandocmsg.messenger.common.nats.MessageDownstreamRouting;
import com.avandocmsg.messenger.common.nats.NatsConnectionOptions;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.common.scheduling.ScheduledTaskSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.prometheus.client.hotspot.DefaultExports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bot webhook MVP: optional per-chat URLs from {@code bot_webhook_subscriptions} when migrated; otherwise
 * {@code BOT_WEBHOOK_URL}. POSTs metadata only plus {@code event_id} (= {@code messageId}) for dedup; logs duplicates.
 */
public class BotDeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(BotDeliveryWorker.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final String QUEUE_GROUP = "bot-delivery-workers";
    private final Connection connection;
    private final DataSource dataSource;
    private final HttpClient httpClient;
    private final String fallbackWebhookUrl;
    private final String webhookHmacSecret;
    private final boolean subscriptionsEnabled;
    private final UserMessageSource workerMessages;
    private final BotWebhookOutbox outbox;
    private final boolean outboxEnabled;
    private final ScheduledExecutorService retryScheduler;

    private final BotDeliveryDedupCache delivered = BotDeliveryDedupCache.fromEnv();

    public BotDeliveryWorker(String natsUrl, DataSource dataSource, String fallbackWebhookUrl,
                             String webhookHmacSecret, boolean subscriptionsEnabled,
                             UserMessageSource workerMessages) throws Exception {
        this(natsUrl, dataSource, fallbackWebhookUrl, webhookHmacSecret, subscriptionsEnabled,
            workerMessages, java.time.Clock.systemUTC());
    }

    BotDeliveryWorker(String natsUrl, DataSource dataSource, String fallbackWebhookUrl,
                      String webhookHmacSecret, boolean subscriptionsEnabled,
                      UserMessageSource workerMessages, java.time.Clock clock) throws Exception {
        this.dataSource = dataSource;
        this.fallbackWebhookUrl =
            fallbackWebhookUrl != null && !fallbackWebhookUrl.isBlank() ? fallbackWebhookUrl.trim() : null;
        this.webhookHmacSecret =
            webhookHmacSecret != null && !webhookHmacSecret.isBlank() ? webhookHmacSecret.trim() : null;
        this.subscriptionsEnabled = subscriptionsEnabled;
        this.workerMessages = workerMessages;
        this.httpClient = HttpClientSupport.sharedClient();
        this.outboxEnabled = BotWebhookOutbox.tablePresent(dataSource);
        this.outbox = outboxEnabled ? new BotWebhookOutbox(dataSource, clock) : null;
        this.retryScheduler = outboxEnabled
            ? Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(r, "bot-webhook-retry");
                t.setDaemon(true);
                return t;
            })
            : null;
        var options = NatsConnectionOptions.clientBuilder(natsUrl, "bot-delivery-worker").build();
        this.connection = Nats.connect(options);
        log.info(workerMessages.format("worker.common.connected_nats", natsUrl));
    }

    public void start() {
        var dispatcher = connection.createDispatcher(this::handle);
        dispatcher.subscribe(NatsSubjects.MSG_EVENT_DOWNSTREAM, QUEUE_GROUP);
        var modeHint = subscriptionsEnabled
            ? "(per-chat subscriptions enabled)"
            : "(fallback BOT_WEBHOOK_URL only)";
        log.info(workerMessages.format("worker.common.subscribed_extra",
            NatsSubjects.MSG_EVENT_DOWNSTREAM, QUEUE_GROUP, modeHint));
        if (MessageDownstreamRouting.legacySubscribeEnabled()) {
            dispatcher.subscribe(NatsSubjects.MSG_EVENT_BOT, QUEUE_GROUP);
            log.info(workerMessages.format("worker.common.subscribed_extra",
                NatsSubjects.MSG_EVENT_BOT, QUEUE_GROUP, modeHint));
        }
        if (retryScheduler != null) {
            ScheduledTaskSupport.scheduleAtFixedRateWithJitter(
                retryScheduler, this::processOutboxRetries, 15, 30, 5000L, TimeUnit.SECONDS);
            ScheduledTaskSupport.scheduleAtFixedRateWithJitter(
                retryScheduler, this::purgeFailedOutbox, 5, 60, 30_000L, TimeUnit.MINUTES);
            log.info(workerMessages.format("worker.bot_delivery.outbox_retry_enabled"));
        }
    }

    private void handle(io.nats.client.Message msg) {
        try {
            MessageDownstreamRouting.dispatchDownstreamMessage(
                msg, MessageDownstreamRouting.ROUTE_BOT, MAPPER, this::processWorkerEvent);
        } catch (MessageDownstreamRouting.DownstreamDispatchException e) {
            log.error(workerMessages.get("worker.bot_delivery.handle_failed"), e.getCause());
        }
    }

    private void processWorkerEvent(MessageWorkerEvent event) {
        try {
            var targets = resolveTargets(event);
            if (targets.isEmpty()) {
                log.warn(workerMessages.format("worker.bot_delivery.no_webhook_targets", event.chatId(), event.messageId()));
                return;
            }
            var json = buildPayload(event);
            for (var target : targets) {
                if (target.botId() != null) {
                    enqueueUpdate(target.botId(), json);
                }
                deliver(target.botId(), target.webhookUrl(), event, json);
            }
        } catch (Exception e) {
            log.error(workerMessages.get("worker.bot_delivery.handle_failed"), e);
        }
    }

    private record DeliveryTarget(UUID botId, String webhookUrl) {}

    private List<DeliveryTarget> resolveTargets(MessageWorkerEvent event) throws SQLException {
        return resolveTargets(event, new BotDeliveryTargets.Prefetch(dataSource));
    }

    /** Package-visible for FR-127 batch prefetch tests. */
    List<DeliveryTarget> resolveTargetsBatch(List<MessageWorkerEvent> events) throws SQLException {
        var prefetch = new BotDeliveryTargets.Prefetch(dataSource);
        var all = new ArrayList<DeliveryTarget>();
        for (var event : events) {
            all.addAll(resolveTargets(event, prefetch));
        }
        return all;
    }

    private List<DeliveryTarget> resolveTargets(MessageWorkerEvent event, BotDeliveryTargets.Prefetch prefetch)
        throws SQLException {
        var targets = new ArrayList<DeliveryTarget>();
        if (subscriptionsEnabled) {
            var chatId = UUID.fromString(event.chatId());
            var rows = prefetch.forChat(chatId);
            for (var resolved : BotDeliveryTargets.resolveForEvent(event, rows)) {
                targets.add(new DeliveryTarget(resolved.botId(), resolved.webhookUrl()));
            }
        }
        if (targets.isEmpty() && fallbackWebhookUrl != null) {
            targets.add(new DeliveryTarget(null, fallbackWebhookUrl));
        }
        return targets;
    }

    private void enqueueUpdate(UUID botId, String payloadJson) {
        if (botId == null) {
            return;
        }
        try (var conn = dataSource.getConnection()) {
            if (!BotDeliveryTargets.tableExists(conn, "bot_updates")) {
                return;
            }
            var sql = """
                INSERT INTO bot_updates (bot_id, event_type, payload)
                VALUES (?, 'message', ?::jsonb)
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, botId);
                stmt.setString(2, payloadJson);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            log.warn(workerMessages.format("worker.bot_delivery.updates_enqueue_failed", e.getMessage()));
        }
    }

    private void deliver(UUID botId, String webhookUrl, MessageWorkerEvent event, String json) {
        var dedupKey = event.messageId() + "|" + webhookUrl;
        var first = delivered.markIfFirst(dedupKey);
        if (!first) {
            log.warn(workerMessages.format("worker.bot_delivery.duplicate_delivery", event.messageId(), webhookUrl));
        }
        try {
            var status = postWebhook(httpClient, webhookUrl, json, webhookHmacSecret);
            log.info(workerMessages.format("worker.bot_delivery.webhook_status",
                status, event.messageId(), webhookUrl));
            if (status < 200 || status >= 300) {
                persistFailedDelivery(botId, event, webhookUrl, json);
            }
        } catch (Exception e) {
            log.warn(workerMessages.format("worker.bot_delivery.webhook_failed", event.messageId(), webhookUrl, e.getMessage()));
            persistFailedDelivery(botId, event, webhookUrl, json);
        }
    }

    private void persistFailedDelivery(UUID botId, MessageWorkerEvent event, String webhookUrl, String json) {
        if (outbox == null) {
            return;
        }
        try {
            var chatId = UUID.fromString(event.chatId());
            outbox.enqueue(botId, chatId, event.messageId(), webhookUrl, json);
            log.info(workerMessages.format("worker.bot_delivery.outbox_enqueued", event.messageId(), webhookUrl));
        } catch (Exception e) {
            log.warn(workerMessages.format("worker.bot_delivery.outbox_enqueue_failed", e.getMessage()));
        }
    }

    void processOutboxRetries() {
        if (outbox == null) {
            return;
        }
        try {
            for (var pending : outbox.fetchDue(20)) {
                retryOutboxRow(pending);
            }
        } catch (Exception e) {
            log.warn(workerMessages.format("worker.bot_delivery.outbox_retry_scan_failed", e.getMessage()));
        }
    }

    void purgeFailedOutbox() {
        if (outbox == null) {
            return;
        }
        var retentionDays = BotWebhookOutbox.failedRetentionDaysFromEnv();
        if (retentionDays <= 0) {
            return;
        }
        try {
            var deleted = outbox.purgeFailed(retentionDays, BotWebhookOutbox.failedPurgeBatchFromEnv());
            if (deleted > 0) {
                log.info(workerMessages.format("worker.bot_delivery.outbox_failed_purged", deleted, retentionDays));
            }
        } catch (Exception e) {
            log.warn(workerMessages.format("worker.bot_delivery.outbox_failed_purge_failed", e.getMessage()));
        }
    }

    private void retryOutboxRow(BotWebhookOutbox.PendingDelivery pending) {
        try {
            var status = postWebhook(httpClient, pending.webhookUrl(), pending.payloadJson(), webhookHmacSecret);
            if (status >= 200 && status < 300) {
                outbox.markDelivered(pending.id());
                log.info(workerMessages.format("worker.bot_delivery.outbox_delivered",
                    pending.eventId(), pending.webhookUrl(), pending.attempts() + 1));
                return;
            }
            outbox.scheduleRetry(pending.id(), pending.attempts());
            log.warn(workerMessages.format("worker.bot_delivery.outbox_retry_status",
                status, pending.eventId(), pending.webhookUrl()));
        } catch (Exception e) {
            try {
                outbox.scheduleRetry(pending.id(), pending.attempts());
            } catch (Exception retryEx) {
                log.warn(workerMessages.format("worker.bot_delivery.outbox_retry_failed", pending.eventId(), retryEx.getMessage()));
            }
            log.warn(workerMessages.format("worker.bot_delivery.outbox_retry_error",
                pending.eventId(), e.getMessage()));
        }
    }

    /** Package-visible for unit tests (BOT-6 mock HTTP server). */
    static int postWebhook(HttpClient httpClient, String webhookUrl, String json, String hmacSecret) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(webhookUrl))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        var signature = BotWebhookSigner.signSha256Hex(hmacSecret, json);
        if (signature != null) {
            builder.header(BotWebhookSigner.SIGNATURE_HEADER, signature);
        }
        var req = builder.build();
        var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return resp.statusCode();
    }

    static String buildPayload(MessageWorkerEvent event) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("event_id", event.messageId());
        root.put("messageId", event.messageId());
        root.put("chatId", event.chatId());
        root.put("senderId", event.senderId());
        root.put("clientMsgId", event.clientMsgId());
        root.put("createdAtEpochMs", event.createdAtEpochMs() != null ? event.createdAtEpochMs() : Instant.now().toEpochMilli());
        root.put("type", event.type());
        root.put("flags", event.flags());
        root.put("encrypted", event.encrypted());
        if (event.storageByteLength() != null) {
            root.put("storageByteLength", event.storageByteLength());
        }
        root.put("dispatchedAtEpochMs", Instant.now().toEpochMilli());
        return root.toString();
    }

    boolean healthReady() {
        return WorkerDependencyHealth.natsAndJdbc(connection, dataSource);
    }

    public void shutdown() {
        if (retryScheduler != null) {
            retryScheduler.shutdownNow();
        }
        try {
            connection.close();
        } catch (Exception e) {
            log.warn(workerMessages.get("worker.common.nats_close_error"), e);
        }
        HikariDataSources.closeQuietly(dataSource);
    }

    static boolean detectSubscriptionsTable(DataSource ds, UserMessageSource workerMessages) {
        try (var c = ds.getConnection()) {
            return BotDeliveryTargets.tableExists(c, BotDeliveryTargets.SUBSCRIPTIONS_TABLE);
        } catch (SQLException e) {
            LoggerFactory.getLogger(BotDeliveryWorker.class)
                .warn(workerMessages.format("worker.common.schema_inspect_failed",
                    BotDeliveryTargets.SUBSCRIPTIONS_TABLE), e);
            return false;
        }
    }

    public static void main(String[] args) {
        var workerMessages = WorkerMessageSources.forWorker(
            BotDeliveryWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_bot_delivery");
        log.info(workerMessages.format("worker.common.locale", workerMessages.locale()));
        var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        var jdbcUrl = firstNonBlank(System.getenv("BOT_DB_JDBC_URL"), System.getenv("DB_JDBC_URL"));
        var user = System.getenv().getOrDefault("DB_USER", "avandocmsg");
        var password = System.getenv().getOrDefault("DB_PASSWORD", "avandocmsg");
        var fallback = System.getenv("BOT_WEBHOOK_URL");
        var hmacSecret = System.getenv("BOT_WEBHOOK_HMAC_SECRET");

        var ds = HikariDataSources.createOptionalPool(jdbcUrl, user, password, 5, "bot-delivery-db");
        if (ds == null) {
            log.error(workerMessages.format("worker.common.db_url_required",
                workerMessages.get("worker.bot_delivery.db_url_required"),
                workerMessages.get("worker.bot_delivery.worker_name")));
            System.exit(2);
        }
        var subs = detectSubscriptionsTable(ds, workerMessages);

        try {
            DefaultExports.initialize();
            BotDeliveryMetricsHttpServer metricsServer = null;
            var metricsPort = parseMetricsPort(System.getenv("BOT_DELIVERY_METRICS_PORT"));
            var worker = new BotDeliveryWorker(natsUrl, ds, fallback, hmacSecret, subs, workerMessages);
            if (metricsPort > 0) {
                metricsServer = BotDeliveryMetricsHttpServer.start(metricsPort, worker::healthReady, workerMessages);
                log.info(workerMessages.format("worker.bot_delivery.metrics_url", metricsServer.getPort()));
            }
            worker.start();
            var finalMetricsServer = metricsServer;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (finalMetricsServer != null) {
                    finalMetricsServer.close();
                }
                worker.shutdown();
            }));
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error(workerMessages.get("worker.common.fatal_error"), e);
            System.exit(1);
        }
    }

    private static int parseMetricsPort(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            var port = Integer.parseInt(raw.trim());
            return port > 0 && port <= 65535 ? port : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }
}
