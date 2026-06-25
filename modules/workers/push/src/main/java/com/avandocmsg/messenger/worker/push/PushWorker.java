package com.avandocmsg.messenger.worker.push;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.http.HttpClientSupport;
import com.avandocmsg.messenger.common.jdbc.HikariDataSources;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.avandocmsg.messenger.common.nats.MessageDownstreamRouting;
import com.avandocmsg.messenger.common.nats.NatsConnectionOptions;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.common.resilience.SimpleCircuitBreaker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.nats.client.Connection;
import io.prometheus.client.hotspot.DefaultExports;
import io.nats.client.Nats;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Push routing: resolves {@code devices.push_token} for chat members (excluding sender),
 * sends Web Push for provider {@code web} when {@code PUSH_VAPID_*} is configured,
 * optionally POSTs metadata to {@code PUSH_WEBHOOK_URL}.
 */
public class PushWorker {
    private static final Logger log = LoggerFactory.getLogger(PushWorker.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final String QUEUE_GROUP = "push-workers";

    private final Connection connection;
    private final DataSource dataSource;
    private final HttpClient httpClient;
    private final String pushWebhookUrl;
    private final WebPushDelivery webPushDelivery;
    private final UserMessageSource workerMessages;
    private final int deviceQueryLimit;
    private final SimpleCircuitBreaker webhookCircuit;

    public PushWorker(String natsUrl, DataSource dataSource, String pushWebhookUrl,
                      UserMessageSource workerMessages) throws Exception {
        this(natsUrl, dataSource, pushWebhookUrl, WebPushDelivery.fromEnvironment(workerMessages), workerMessages);
    }

    PushWorker(String natsUrl, DataSource dataSource, String pushWebhookUrl, WebPushDelivery webPushDelivery,
               UserMessageSource workerMessages)
        throws Exception {
        this.dataSource = dataSource;
        this.pushWebhookUrl = pushWebhookUrl != null && !pushWebhookUrl.isBlank() ? pushWebhookUrl.trim() : null;
        this.webPushDelivery = webPushDelivery != null ? webPushDelivery : WebPushDelivery.disabled(workerMessages);
        this.workerMessages = workerMessages;
        this.deviceQueryLimit = PushPlatformDefaults.deviceQueryLimit();
        this.webhookCircuit = new SimpleCircuitBreaker(5, Duration.ofSeconds(30));
        this.httpClient = HttpClientSupport.sharedClient();
        var options = NatsConnectionOptions.clientBuilder(natsUrl, "push-worker").build();
        this.connection = Nats.connect(options);
        log.info(workerMessages.format("worker.common.connected_nats", natsUrl));
    }

    public void start() {
        var dispatcher = connection.createDispatcher(this::handle);
        dispatcher.subscribe(NatsSubjects.MSG_EVENT_DOWNSTREAM, QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed", NatsSubjects.MSG_EVENT_DOWNSTREAM, QUEUE_GROUP));
        if (MessageDownstreamRouting.legacySubscribeEnabled()) {
            dispatcher.subscribe(NatsSubjects.MSG_EVENT_PUSH, QUEUE_GROUP);
            log.info(workerMessages.format("worker.common.subscribed", NatsSubjects.MSG_EVENT_PUSH, QUEUE_GROUP));
        }
    }

    private void handle(io.nats.client.Message msg) {
        try {
            MessageDownstreamRouting.dispatchDownstreamMessage(
                msg, MessageDownstreamRouting.ROUTE_PUSH, MAPPER, this::processWorkerEvent);
        } catch (MessageDownstreamRouting.DownstreamDispatchException e) {
            log.error(workerMessages.get("worker.push.handle_failed"), e.getCause());
        }
    }

    private void processWorkerEvent(MessageWorkerEvent event) {
        try {
            var devices = loadTargetDevices(event);
            log.info(workerMessages.format("worker.push.targets",
                event.messageId(), event.chatId(), devices.size()));
            for (var d : devices) {
                log.debug(workerMessages.format("worker.push.token_row",
                    d.userId(), d.provider(), maskToken(d.token())));
            }
            deliverWebPush(event, devices);
            if (pushWebhookUrl != null) {
                postWebhook(event, devices);
            }
        } catch (Exception e) {
            log.error(workerMessages.get("worker.push.handle_failed"), e);
        }
    }

    private void deliverWebPush(MessageWorkerEvent event, List<DeviceRow> devices) {
        if (!webPushDelivery.isEnabled() || devices.isEmpty()) {
            return;
        }
        String chatTitle = null;
        try {
            if (event.chatId() != null && !event.chatId().isBlank()) {
                chatTitle = loadChatTitle(UUID.fromString(event.chatId()));
            }
        } catch (Exception e) {
            log.debug(workerMessages.format("worker.push.chat_title_failed", e.getMessage()));
        }
        var sent = 0;
        var failed = 0;
        var expired = 0;
        var mentionedUserIds = loadMentionedUserIds(event);
        for (var d : devices) {
            if (!WebPushDelivery.isWebProvider(d.provider())) {
                continue;
            }
            var preview = PushNotificationPreview.forEvent(event, chatTitle, workerMessages,
                isUserMentioned(mentionedUserIds, d.userId()));
            var result = webPushDelivery.send(d.token(), preview);
            switch (result) {
                case SENT -> sent++;
                case EXPIRED -> {
                    expired++;
                    clearPushToken(d.userId(), d.token());
                }
                case FAILED -> failed++;
            }
        }
        if (sent > 0 || failed > 0 || expired > 0) {
            PushMetrics.webPushSent(sent);
            PushMetrics.webPushFailed(failed);
            PushMetrics.webPushExpired(expired);
            log.info(workerMessages.format("worker.push.web_push_summary",
                event.messageId(), sent, failed, expired));
        }
    }

    private void clearPushToken(String userId, String pushToken) {
        if (userId == null || pushToken == null || pushToken.isBlank()) {
            return;
        }
        var sql = """
            UPDATE devices SET push_token = NULL, push_provider = NULL, last_active_at = now()
            WHERE user_id = ? AND push_token = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, UUID.fromString(userId));
            stmt.setString(2, pushToken);
            var n = stmt.executeUpdate();
            if (n > 0) {
                log.info(workerMessages.format("worker.push.token_cleared", userId));
            }
        } catch (Exception e) {
            log.warn(workerMessages.format("worker.push.token_clear_failed", userId, e.getMessage()));
        }
    }

    private String loadChatTitle(UUID chatId) throws SQLException {
        var sql = "SELECT title FROM chats WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    private void postWebhook(MessageWorkerEvent event, List<DeviceRow> devices) throws Exception {
        if (!webhookCircuit.allowCall()) {
            PushMetrics.webhookCircuitSkip();
            log.warn(workerMessages.get("worker.push.webhook_circuit_open"));
            return;
        }
        var body = buildWebhookPayload(event, devices);
        var req = HttpRequest.newBuilder(URI.create(pushWebhookUrl))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        try {
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 500) {
                webhookCircuit.recordFailure();
            } else {
                webhookCircuit.recordSuccess();
            }
            log.info(workerMessages.format("worker.push.webhook_status", resp.statusCode(), event.messageId()));
        } catch (Exception e) {
            webhookCircuit.recordFailure();
            throw e;
        }
    }

    private String buildWebhookPayload(MessageWorkerEvent event, List<DeviceRow> devices) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("messageId", event.messageId());
        root.put("chatId", event.chatId());
        root.put("senderId", event.senderId());
        root.put("clientMsgId", event.clientMsgId());
        root.put("createdAtEpochMs", event.createdAtEpochMs() != null ? event.createdAtEpochMs() : Instant.now().toEpochMilli());
        root.put("type", event.type());
        root.put("flags", event.flags());
        root.put("encrypted", event.encrypted());
        root.put("storageByteLength", event.storageByteLength() != null ? event.storageByteLength() : 0);
        root.put("deviceCount", devices.size());
        ArrayNode providers = MAPPER.createArrayNode();
        var seen = new LinkedHashSet<String>();
        for (var d : devices) {
            if (d.provider() != null && seen.add(d.provider())) {
                providers.add(d.provider());
            }
        }
        root.set("pushProviders", providers);
        root.put("issuedAtEpochMs", Instant.now().toEpochMilli());
        return root.toString();
    }

    private Set<UUID> loadMentionedUserIds(MessageWorkerEvent event) {
        if (event == null || event.messageId() == null || event.messageId().isBlank()) {
            return Set.of();
        }
        try {
            var messageId = UUID.fromString(event.messageId());
            var sql = "SELECT user_id FROM message_mentions WHERE message_id = ?";
            var out = new HashSet<UUID>();
            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, messageId);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getObject(1, UUID.class));
                    }
                }
            }
            return out;
        } catch (Exception e) {
            log.debug(workerMessages.format("worker.push.mention_check_failed", e.getMessage()));
            return Set.of();
        }
    }

    private static boolean isUserMentioned(Set<UUID> mentionedUserIds, String userId) {
        if (mentionedUserIds == null || mentionedUserIds.isEmpty() || userId == null || userId.isBlank()) {
            return false;
        }
        try {
            return mentionedUserIds.contains(UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Package-visible for unit tests (batch mention lookup). */
    static boolean isUserMentionedForTest(Set<UUID> mentionedUserIds, String userId) {
        return isUserMentioned(mentionedUserIds, userId);
    }

    private List<DeviceRow> loadTargetDevices(MessageWorkerEvent event) throws SQLException {
        return loadTargetDevices(dataSource, event, deviceQueryLimit);
    }

    /** Package-visible for unit tests (FR-021 device query cap). */
    static List<DeviceRow> loadTargetDevices(DataSource dataSource, MessageWorkerEvent event, int limit)
        throws SQLException {
        var sql = """
            SELECT d.user_id, d.push_provider, d.push_token
            FROM chat_members cm
            INNER JOIN devices d ON d.user_id = cm.user_id
            WHERE cm.chat_id = ?
              AND cm.user_id <> ?
              AND d.push_token IS NOT NULL
              AND trim(d.push_token) <> ''
            LIMIT ?
            """;
        var chatId = UUID.fromString(event.chatId());
        var senderId = UUID.fromString(event.senderId());
        var out = new ArrayList<DeviceRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, senderId);
            stmt.setInt(3, Math.max(1, limit));
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var uid = rs.getObject(1, UUID.class);
                    var provider = rs.getString(2);
                    var token = rs.getString(3);
                    out.add(new DeviceRow(uid.toString(), provider, token));
                }
            }
        }
        return out;
    }

    private static String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "…" + token.substring(token.length() - 4);
    }

    record DeviceRow(String userId, String provider, String token) {
    }

    Connection natsConnection() {
        return connection;
    }

    DataSource dataSource() {
        return dataSource;
    }

    public void shutdown() {
        try {
            connection.close();
        } catch (Exception e) {
            log.warn(workerMessages.get("worker.common.nats_close_error"), e);
        }
        HikariDataSources.closeQuietly(dataSource);
    }

    public static void main(String[] args) {
        var workerMessages = WorkerMessageSources.forWorker(
            PushWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_push");
        log.info(workerMessages.format("worker.common.locale", workerMessages.locale()));
        var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        var jdbcUrl = firstNonBlank(System.getenv("PUSH_DB_JDBC_URL"), System.getenv("DB_JDBC_URL"));
        var user = System.getenv().getOrDefault("DB_USER", "avandocmsg");
        var password = System.getenv().getOrDefault("DB_PASSWORD", "avandocmsg");
        var webhook = System.getenv("PUSH_WEBHOOK_URL");

        var ds = HikariDataSources.createOptionalPool(jdbcUrl, user, password, 8, "push-worker-db");
        if (ds == null) {
            log.error(workerMessages.format("worker.common.db_url_required",
                workerMessages.get("worker.push.db_url_required"),
                workerMessages.get("worker.push.worker_name")));
            System.exit(2);
        }

        PushHealthHttpServer healthServer = null;
        try {
            DefaultExports.initialize();
            var worker = new PushWorker(natsUrl, ds, webhook, workerMessages);
            worker.start();
            var metricsPort = PushPlatformDefaults.metricsPort();
            if (metricsPort > 0) {
                healthServer = PushHealthHttpServer.start(metricsPort,
                    new PushHealthProbe(worker.natsConnection(), worker.dataSource()), workerMessages);
                log.info(workerMessages.format("worker.push.metrics_url", healthServer.getPort()));
            }
            PushHealthHttpServer healthRef = healthServer;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (healthRef != null) {
                    healthRef.close();
                }
                worker.shutdown();
            }));
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
