package com.avandocmsg.messenger.worker.preview;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.health.WorkerDependencyHealth;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.avandocmsg.messenger.common.jdbc.HikariDataSources;
import com.avandocmsg.messenger.common.nats.MessageDownstreamRouting;
import com.avandocmsg.messenger.common.nats.NatsConnectionOptions;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Nats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.prometheus.client.hotspot.DefaultExports;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.UUID;

/**
 * Link preview MVP: consumes {@link NatsSubjects#MSG_EVENT_DOWNSTREAM} (route {@code index}),
 * optionally reads {@code messages.content}
 * from the hot DB when {@code PREVIEW_DB_JDBC_URL} is set, extracts the first HTTP(S) URL, runs an SSRF-aware GET,
 * caches titles in-memory (TTL). Development-only {@code PREVIEW_TEST_URL} is used when no URL is found in plaintext.
 */
public class PreviewWorker {
    private static final Logger log = LoggerFactory.getLogger(PreviewWorker.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final String QUEUE_GROUP = "preview-workers";

    private final Connection connection;
    private final DataSource previewDataSource;
    private final MessageContentLoader contentLoader;
    private final MessageLinkPreviewStore linkPreviewStore;
    private final LinkPreviewFetcher fetcher;
    private final TtlStringCache cache;
    private final String previewTestUrl;
    private final UserMessageSource workerMessages;

    public PreviewWorker(String natsUrl, DataSource previewDataSource, LinkPreviewFetcher fetcher, TtlStringCache cache,
                         String previewTestUrl, UserMessageSource workerMessages) throws Exception {
        this.previewDataSource = previewDataSource;
        this.workerMessages = workerMessages;
        this.contentLoader = previewDataSource != null ? new MessageContentLoader(previewDataSource, workerMessages) : null;
        this.linkPreviewStore = previewDataSource != null ? new MessageLinkPreviewStore(previewDataSource) : null;
        this.fetcher = fetcher;
        this.cache = cache;
        this.previewTestUrl = previewTestUrl != null && !previewTestUrl.isBlank() ? previewTestUrl.trim() : null;
        var options = NatsConnectionOptions.clientBuilder(natsUrl, "preview-worker").build();
        this.connection = Nats.connect(options);
        log.info(workerMessages.format("worker.common.connected_nats", natsUrl));
    }

    public void start() {
        var dispatcher = connection.createDispatcher(this::handle);
        dispatcher.subscribe(NatsSubjects.MSG_EVENT_DOWNSTREAM, QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed", NatsSubjects.MSG_EVENT_DOWNSTREAM, QUEUE_GROUP));
        if (MessageDownstreamRouting.legacySubscribeEnabled()) {
            dispatcher.subscribe(NatsSubjects.MSG_EVENT_INDEX, QUEUE_GROUP);
            log.info(workerMessages.format("worker.common.subscribed", NatsSubjects.MSG_EVENT_INDEX, QUEUE_GROUP));
        }
    }

    private void handle(io.nats.client.Message msg) {
        try {
            MessageDownstreamRouting.dispatchDownstreamMessage(
                msg, MessageDownstreamRouting.ROUTE_INDEX, MAPPER, this::processWorkerEvent);
        } catch (MessageDownstreamRouting.DownstreamDispatchException e) {
            log.error(workerMessages.get("worker.preview.handle_failed"), e.getCause());
        }
    }

    private void processWorkerEvent(MessageWorkerEvent event) {
        try {
            resolveAndFetch(event);
        } catch (Exception e) {
            log.error(workerMessages.get("worker.preview.handle_failed"), e);
        }
    }

    private void resolveAndFetch(MessageWorkerEvent event) {
        if ("delete".equalsIgnoreCase(event.indexOp())) {
            return;
        }
        String targetUrl = null;
        if (!event.encrypted() && contentLoader != null) {
            var id = UUID.fromString(event.messageId());
            var content = contentLoader.loadContent(id).orElse(null);
            if (content != null) {
                targetUrl = UrlExtractor.firstHttpUrl(content).orElse(null);
            }
        } else if (event.encrypted()) {
            log.trace("Skipping plaintext URL extraction for encrypted messageId={}", event.messageId());
        }
        if (targetUrl == null && previewTestUrl != null) {
            targetUrl = previewTestUrl;
            log.trace("Using PREVIEW_TEST_URL for messageId={}", event.messageId());
        }
        if (targetUrl == null) {
            log.trace("No preview URL for messageId={}", event.messageId());
            return;
        }
        var cached = cache.get(targetUrl);
        if (cached.isPresent()) {
            log.debug(workerMessages.format("worker.preview.cache_hit", event.messageId(), targetUrl, cached.get()));
            return;
        }
        var title = fetcher.fetchPreviewTitle(targetUrl).orElse("(no title)");
        cache.put(targetUrl, title);
        if (linkPreviewStore != null) {
            linkPreviewStore.upsert(UUID.fromString(event.messageId()), targetUrl, title);
        }
        log.info(workerMessages.format("worker.preview.link_preview", event.messageId(), targetUrl, title));
    }

    public boolean natsConnected() {
        return connection.getStatus() == Connection.Status.CONNECTED;
    }

    boolean healthReady() {
        return WorkerDependencyHealth.natsAndOptionalJdbc(connection, previewDataSource);
    }

    public void shutdown() {
        try {
            connection.close();
        } catch (Exception e) {
            log.warn(workerMessages.get("worker.common.nats_close_error"), e);
        }
        HikariDataSources.closeQuietly(previewDataSource);
    }

    public static void main(String[] args) {
        var workerMessages = WorkerMessageSources.forWorker(
            PreviewWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_preview");
        log.info(workerMessages.format("worker.common.locale", workerMessages.locale()));
        var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        var previewJdbc = System.getenv("PREVIEW_DB_JDBC_URL");
        var previewUser = System.getenv().getOrDefault("PREVIEW_DB_USER", System.getenv().getOrDefault("DB_USER", "avandocmsg"));
        var previewPassword =
            System.getenv().getOrDefault("PREVIEW_DB_PASSWORD", System.getenv().getOrDefault("DB_PASSWORD", "avandocmsg"));
        var testUrl = System.getenv("PREVIEW_TEST_URL");
        var ttlSec = parseLongEnv("PREVIEW_CACHE_TTL_SECONDS", 3600);
        var maxBytes = (int) parseLongEnv("PREVIEW_MAX_BYTES", 65_536);
        var timeoutMs = parseLongEnv("PREVIEW_HTTP_TIMEOUT_MS", 5000);

        var previewDs = HikariDataSources.createOptionalPool(previewJdbc, previewUser, previewPassword, 5, "preview-hot");
        var cache = new TtlStringCache(Duration.ofSeconds(ttlSec));
        var fetcher = new LinkPreviewFetcher(
            Duration.ofMillis(timeoutMs), Duration.ofMillis(timeoutMs), maxBytes, workerMessages);

        PreviewHealthHttpServer healthServer = null;
        try {
            DefaultExports.initialize();
            var worker = new PreviewWorker(natsUrl, previewDs, fetcher, cache, testUrl, workerMessages);
            worker.start();
            var metricsPort = parseIntEnv("PREVIEW_METRICS_PORT", 9191);
            if (metricsPort > 0) {
                healthServer = PreviewHealthHttpServer.start(metricsPort,
                    (PreviewReadinessCheck) worker::healthReady,
                    workerMessages);
                log.info(workerMessages.format("worker.preview.metrics_url", healthServer.getPort()));
            }
            PreviewHealthHttpServer healthRef = healthServer;
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

    private static int parseIntEnv(String key, int defaultVal) {
        var raw = System.getenv(key);
        if (raw == null || raw.isBlank()) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static long parseLongEnv(String key, long defaultVal) {
        var raw = System.getenv(key);
        if (raw == null || raw.isBlank()) {
            return defaultVal;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
