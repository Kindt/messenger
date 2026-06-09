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
    private final boolean subscriptionsEnabled;
    private final UserMessageSource workerMessages;

    private final ConcurrentHashMap<String, Boolean> delivered = new ConcurrentHashMap<>();

    public BotDeliveryWorker(String natsUrl, DataSource dataSource, String fallbackWebhookUrl,
                             boolean subscriptionsEnabled, UserMessageSource workerMessages) throws Exception {
        this.dataSource = dataSource;
        this.fallbackWebhookUrl =
            fallbackWebhookUrl != null && !fallbackWebhookUrl.isBlank() ? fallbackWebhookUrl.trim() : null;
        this.subscriptionsEnabled = subscriptionsEnabled;
        this.workerMessages = workerMessages;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
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
    }

    private void handle(io.nats.client.Message msg) {
        try {
            var payload = new String(msg.getData(), StandardCharsets.UTF_8);
            var event = MAPPER.readValue(payload, MessageWorkerEvent.class);
            var urls = resolveWebhookUrls(event);
            if (urls.isEmpty()) {
                log.warn(workerMessages.format("worker.bot_delivery.no_webhook_targets", event.chatId(), event.messageId()));
                return;
            }
            for (var url : urls) {
                deliver(url, event);
            }
        } catch (Exception e) {
            log.error(workerMessages.get("worker.bot_delivery.handle_failed"), e);
        }
    }

    private List<String> resolveWebhookUrls(MessageWorkerEvent event) throws SQLException {
        var urls = new ArrayList<String>();
        if (subscriptionsEnabled) {
            var sql = "SELECT webhook_url FROM " + SUBSCRIPTIONS_TABLE + " WHERE chat_id = ?";
            var chatId = UUID.fromString(event.chatId());
            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, chatId);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        var u = rs.getString(1);
                        if (u != null && !u.isBlank()) {
                            urls.add(u.trim());
                        }
                    }
                }
            }
        }
        if (urls.isEmpty() && fallbackWebhookUrl != null) {
            urls.add(fallbackWebhookUrl);
        }
        return urls;
    }

    private void deliver(String webhookUrl, MessageWorkerEvent event) throws Exception {
        var dedupKey = event.messageId() + "|" + webhookUrl;
        var first = delivered.putIfAbsent(dedupKey, Boolean.TRUE) == null;
        if (!first) {
            log.warn(workerMessages.format("worker.bot_delivery.duplicate_delivery", event.messageId(), webhookUrl));
        }
        var json = buildPayload(event);
        var req = HttpRequest.newBuilder(URI.create(webhookUrl))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();
        var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        log.info(workerMessages.format("worker.bot_delivery.webhook_status",
            resp.statusCode(), event.messageId(), webhookUrl));
    }

    private static String buildPayload(MessageWorkerEvent event) {
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

        var ds = HikariDataSources.createOptionalPool(jdbcUrl, user, password, 5, "bot-delivery-db");
        if (ds == null) {
            log.error(workerMessages.format("worker.common.db_url_required",
                workerMessages.get("worker.bot_delivery.db_url_required"),
                workerMessages.get("worker.bot_delivery.worker_name")));
            System.exit(2);
        }
        var subs = detectSubscriptionsTable(ds, workerMessages);

        try {
            var worker = new BotDeliveryWorker(natsUrl, ds, fallback, subs, workerMessages);
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
