package com.avandocmsg.messenger.worker.botdelivery;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.avandocmsg.messenger.common.jdbc.HikariDataSources;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bot webhook MVP: optional per-chat URLs from {@code bot_webhook_subscriptions} when migrated; otherwise
 * {@code BOT_WEBHOOK_URL}. POSTs metadata only plus {@code event_id} (= {@code messageId}) for dedup; logs duplicates.
 */
public class BotDeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(BotDeliveryWorker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String QUEUE_GROUP = "bot-delivery-workers";
    private static final String SUBSCRIPTIONS_TABLE = "bot_webhook_subscriptions";

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

    private final ConcurrentHashMap<String, Boolean> delivered = new ConcurrentHashMap<>();

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
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.outboxEnabled = BotWebhookOutbox.tablePresent(dataSource);
        this.outbox = outboxEnabled ? new BotWebhookOutbox(dataSource, clock) : null;
        this.retryScheduler = outboxEnabled
            ? Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(r, "bot-webhook-retry");
                t.setDaemon(true);
                return t;
            })
            : null;
        var options = Options.builder()
            .server(natsUrl)
            .connectionName("bot-delivery-worker")
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(-1)
            .build();
        this.connection = Nats.connect(options);
        log.info(workerMessages.format("worker.common.connected_nats", natsUrl));
    }

    public void start() {
        var dispatcher = connection.createDispatcher(this::handle);
        dispatcher.subscribe(NatsSubjects.MSG_EVENT_BOT, QUEUE_GROUP);
        var modeHint = subscriptionsEnabled
            ? "(per-chat subscriptions enabled)"
            : "(fallback BOT_WEBHOOK_URL only)";
        log.info(workerMessages.format("worker.common.subscribed_extra",
            NatsSubjects.MSG_EVENT_BOT, QUEUE_GROUP, modeHint));
        if (retryScheduler != null) {
            retryScheduler.scheduleAtFixedRate(this::processOutboxRetries, 15, 30, TimeUnit.SECONDS);
            log.info(workerMessages.format("worker.bot_delivery.outbox_retry_enabled"));
        }
    }

    private void handle(io.nats.client.Message msg) {
        try {
            var payload = new String(msg.getData(), StandardCharsets.UTF_8);
            var event = MAPPER.readValue(payload, MessageWorkerEvent.class);
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
        var targets = new ArrayList<DeliveryTarget>();
        if (subscriptionsEnabled) {
            var chatId = UUID.fromString(event.chatId());
            try (var conn = dataSource.getConnection()) {
                if (tableExists(conn, "bots")) {
                    var sql = """
                        SELECT s.webhook_url, b.bot_name, b.listen_mode, b.default_webhook_url, s.bot_id
                        FROM bot_webhook_subscriptions s
                        LEFT JOIN bots b ON b.id = s.bot_id
                        WHERE s.chat_id = ?
                        """;
                    try (var stmt = conn.prepareStatement(sql)) {
                        stmt.setObject(1, chatId);
                        try (var rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                var botId = (UUID) rs.getObject("bot_id");
                                var webhook = rs.getString("webhook_url");
                                if (botId == null) {
                                    addTarget(targets, null, webhook);
                                    continue;
                                }
                                var botName = rs.getString("bot_name");
                                var listenMode = rs.getString("listen_mode");
                                var defaultUrl = rs.getString("default_webhook_url");
                                if (!BotEventFilter.shouldDeliver(event, botName, listenMode)) {
                                    continue;
                                }
                                var effective = webhook != null && !webhook.isBlank() ? webhook : defaultUrl;
                                addTarget(targets, botId, effective);
                            }
                        }
                    }
                } else {
                    var sql = "SELECT webhook_url FROM " + SUBSCRIPTIONS_TABLE + " WHERE chat_id = ?";
                    try (var stmt = conn.prepareStatement(sql)) {
                        stmt.setObject(1, chatId);
                        try (var rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                addTarget(targets, null, rs.getString(1));
                            }
                        }
                    }
                }
            }
        }
        if (targets.isEmpty() && fallbackWebhookUrl != null) {
            targets.add(new DeliveryTarget(null, fallbackWebhookUrl));
        }
        return targets;
    }

    private static void addTarget(List<DeliveryTarget> targets, UUID botId, String raw) {
        if (raw != null && !raw.isBlank()) {
            targets.add(new DeliveryTarget(botId, raw.trim()));
        }
    }

    private void enqueueUpdate(UUID botId, String payloadJson) {
        if (botId == null) {
            return;
        }
        try (var conn = dataSource.getConnection()) {
            if (!tableExists(conn, "bot_updates")) {
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
        var first = delivered.putIfAbsent(dedupKey, Boolean.TRUE) == null;
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

    private static boolean tableExists(java.sql.Connection conn, String table) throws SQLException {
        var md = conn.getMetaData();
        try (var rs = md.getTables(conn.getSchema(), null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
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
            var md = c.getMetaData();
            try (var rs = md.getTables(c.getSchema(), null, SUBSCRIPTIONS_TABLE, new String[]{"TABLE"})) {
                return rs.next();
            }
        } catch (SQLException e) {
            LoggerFactory.getLogger(BotDeliveryWorker.class)
                .warn(workerMessages.format("worker.common.schema_inspect_failed", SUBSCRIPTIONS_TABLE), e);
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
            var worker = new BotDeliveryWorker(natsUrl, ds, fallback, hmacSecret, subs, workerMessages);
            worker.start();
            Runtime.getRuntime().addShutdownHook(new Thread(worker::shutdown));
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error(workerMessages.get("worker.common.fatal_error"), e);
            System.exit(1);
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
