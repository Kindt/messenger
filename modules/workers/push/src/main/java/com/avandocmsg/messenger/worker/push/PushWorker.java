package com.avandocmsg.messenger.worker.push;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.jdbc.HikariDataSources;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Push routing: resolves {@code devices.push_token} for chat members (excluding sender),
 * sends Web Push for provider {@code web} when {@code PUSH_VAPID_*} is configured,
 * optionally POSTs metadata to {@code PUSH_WEBHOOK_URL}.
 */
public class PushWorker {
    private static final Logger log = LoggerFactory.getLogger(PushWorker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String QUEUE_GROUP = "push-workers";

    private final Connection connection;
    private final DataSource dataSource;
    private final HttpClient httpClient;
    private final String pushWebhookUrl;
    private final WebPushDelivery webPushDelivery;

    public PushWorker(String natsUrl, DataSource dataSource, String pushWebhookUrl) throws Exception {
        this(natsUrl, dataSource, pushWebhookUrl, WebPushDelivery.fromEnvironment());
    }

    PushWorker(String natsUrl, DataSource dataSource, String pushWebhookUrl, WebPushDelivery webPushDelivery)
        throws Exception {
        this.dataSource = dataSource;
        this.pushWebhookUrl = pushWebhookUrl != null && !pushWebhookUrl.isBlank() ? pushWebhookUrl.trim() : null;
        this.webPushDelivery = webPushDelivery != null ? webPushDelivery : WebPushDelivery.disabled();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var options = Options.builder()
            .server(natsUrl)
            .connectionName("push-worker")
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(-1)
            .build();
        this.connection = Nats.connect(options);
        log.info("Connected to NATS at {}", natsUrl);
    }

    public void start() {
        var dispatcher = connection.createDispatcher(this::handle);
        dispatcher.subscribe(NatsSubjects.MSG_EVENT_PUSH, QUEUE_GROUP);
        log.info("Subscribed to {} (queue: {})", NatsSubjects.MSG_EVENT_PUSH, QUEUE_GROUP);
    }

    private void handle(io.nats.client.Message msg) {
        try {
            var payload = new String(msg.getData(), StandardCharsets.UTF_8);
            var event = MAPPER.readValue(payload, MessageWorkerEvent.class);
            var devices = loadTargetDevices(event);
            log.info("Push targets messageId={} chatId={} deviceRows={}", event.messageId(), event.chatId(),
                devices.size());
            for (var d : devices) {
                log.debug("Push token row userId={} provider={} tokenPrefix={}", d.userId(), d.provider(),
                    maskToken(d.token()));
            }
            if (pushWebhookUrl != null) {
                postWebhook(event, devices);
            }
        } catch (Exception e) {
            log.error("Failed to handle push message", e);
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
            log.debug("Chat title lookup failed: {}", e.getMessage());
        }
        var preview = PushNotificationPreview.forEvent(event, chatTitle);
        var sent = 0;
        var failed = 0;
        var expired = 0;
        for (var d : devices) {
            if (!WebPushDelivery.isWebProvider(d.provider())) {
                continue;
            }
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
            log.info("Web push messageId={} sent={} failed={} expired={}", event.messageId(), sent, failed,
                expired);
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
                log.info("Cleared expired web push token for userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to clear push token for userId={}: {}", userId, e.getMessage());
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
        var body = buildWebhookPayload(event, devices);
        var req = HttpRequest.newBuilder(URI.create(pushWebhookUrl))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        log.info("PUSH_WEBHOOK_URL status={} messageId={}", resp.statusCode(), event.messageId());
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

    private List<DeviceRow> loadTargetDevices(MessageWorkerEvent event) throws SQLException {
        var sql = """
            SELECT d.user_id, d.push_provider, d.push_token
            FROM chat_members cm
            INNER JOIN devices d ON d.user_id = cm.user_id
            WHERE cm.chat_id = ?
              AND cm.user_id <> ?
              AND d.push_token IS NOT NULL
              AND trim(d.push_token) <> ''
            """;
        var chatId = UUID.fromString(event.chatId());
        var senderId = UUID.fromString(event.senderId());
        var out = new ArrayList<DeviceRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, senderId);
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

    private record DeviceRow(String userId, String provider, String token) {
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
            log.warn("Error closing NATS connection", e);
        }
        HikariDataSources.closeQuietly(dataSource);
    }

    public static void main(String[] args) {
        var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        var jdbcUrl = firstNonBlank(System.getenv("PUSH_DB_JDBC_URL"), System.getenv("DB_JDBC_URL"));
        var user = System.getenv().getOrDefault("DB_USER", "avandocmsg");
        var password = System.getenv().getOrDefault("DB_PASSWORD", "avandocmsg");
        var webhook = System.getenv("PUSH_WEBHOOK_URL");

        var ds = HikariDataSources.createOptionalPool(jdbcUrl, user, password, 8, "push-worker-db");
        if (ds == null) {
            log.error("Set DB_JDBC_URL or PUSH_DB_JDBC_URL for PushWorker");
            System.exit(2);
        }

        PushHealthHttpServer healthServer = null;
        try {
            var worker = new PushWorker(natsUrl, ds, webhook);
            worker.start();
            var metricsPort = PushPlatformDefaults.metricsPort();
            if (metricsPort > 0) {
                healthServer = PushHealthHttpServer.start(metricsPort,
                    new PushHealthProbe(worker.natsConnection(), worker.dataSource()));
                log.info("Push worker health on http://0.0.0.0:{}/health", healthServer.getPort());
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
            log.error("Fatal error", e);
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
