package com.avandocmsg.messenger.worker.archiver;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.health.WorkerDependencyHealth;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.avandocmsg.messenger.common.nats.MessageDownstreamRouting;
import com.avandocmsg.messenger.common.nats.NatsConnectionOptions;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.common.json.MessengerJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.prometheus.client.hotspot.DefaultExports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Archives metadata to an optional cold DB ({@code ARCHIVE_JDBC_URL}), then publishes handoff events to
 * {@link NatsSubjects#MSG_EVENT_DEEP_ARCHIVE} for {@code DeepArchiverWorker}.
 */
public class ArchiverWorker {
    private static final Logger log = LoggerFactory.getLogger(ArchiverWorker.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final String QUEUE_GROUP = "archiver-workers";

    private final Connection connection;
    private final DataSource archiveDataSource;
    private final boolean archiveEnabled;
    private final UserMessageSource workerMessages;

    public ArchiverWorker(String natsUrl, DataSource archiveDataSource, boolean archiveEnabled,
                          UserMessageSource workerMessages) throws Exception {
        this.archiveDataSource = archiveDataSource;
        this.archiveEnabled = archiveEnabled;
        this.workerMessages = workerMessages;
        var options = NatsConnectionOptions.clientBuilder(natsUrl, "archiver-worker").build();
        this.connection = Nats.connect(options);
        log.info(workerMessages.format("worker.common.connected_nats", natsUrl));
    }

    public void start() throws Exception {
        if (archiveEnabled) {
            ensureArchiveTable();
        } else {
            log.info(workerMessages.get("worker.archiver.archive_db_disabled"));
        }
        var dispatcher = connection.createDispatcher(this::handle);
        dispatcher.subscribe(NatsSubjects.MSG_EVENT_DOWNSTREAM, QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed", NatsSubjects.MSG_EVENT_DOWNSTREAM, QUEUE_GROUP));
        if (MessageDownstreamRouting.legacySubscribeEnabled()) {
            dispatcher.subscribe(NatsSubjects.MSG_EVENT_INDEX, QUEUE_GROUP);
            log.info(workerMessages.format("worker.common.subscribed", NatsSubjects.MSG_EVENT_INDEX, QUEUE_GROUP));
        }
    }

    boolean healthReady() {
        return WorkerDependencyHealth.natsConnected(connection)
            && (!archiveEnabled || WorkerDependencyHealth.jdbcReachable(archiveDataSource));
    }

    private void ensureArchiveTable() throws Exception {
        var ddl = """
            CREATE TABLE IF NOT EXISTS archive_message_meta (
              message_id UUID PRIMARY KEY,
              chat_id UUID NOT NULL,
              sender_id UUID NOT NULL,
              client_msg_id TEXT,
              created_at_epoch_ms BIGINT,
              type TEXT,
              flags INT NOT NULL DEFAULT 0,
              encrypted BOOLEAN NOT NULL,
              storage_byte_length INT,
              archived_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """;
        try (var conn = archiveDataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute(ddl);
        }
    }

    private void handle(io.nats.client.Message msg) {
        if (msg.getData() == null || msg.getData().length == 0) {
            return;
        }
        try {
            MessageDownstreamRouting.dispatchDownstreamMessage(
                msg, MessageDownstreamRouting.ROUTE_INDEX, MAPPER, this::processWorkerEvent);
        } catch (MessageDownstreamRouting.DownstreamDispatchException e) {
            log.error(workerMessages.get("worker.archiver.handle_failed"), e.getCause());
        }
    }

    private void processWorkerEvent(MessageWorkerEvent event) {
        try {
            var payload = MAPPER.writeValueAsBytes(event);

            if ("delete".equalsIgnoreCase(event.indexOp())) {
                if (archiveEnabled) {
                    deleteArchiveRow(event.messageId());
                }
                publishDeepArchive(payload, event.messageId());
                return;
            }

            if (!archiveEnabled) {
                publishDeepArchive(payload, event.messageId());
                return;
            }

            if (upsertArchiveRow(event)) {
                publishDeepArchive(payload, event.messageId());
            }
        } catch (Exception e) {
            log.error(workerMessages.get("worker.archiver.handle_failed"), e);
        }
    }

    /**
     * Idempotent by primary key {@code message_id}.
     */
    private void deleteArchiveRow(String messageId) {
        var sql = "DELETE FROM archive_message_meta WHERE message_id = ?";
        try (var conn = archiveDataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, UUID.fromString(messageId));
            stmt.executeUpdate();
        } catch (Exception e) {
            log.warn(workerMessages.format("worker.archiver.delete_failed", messageId), e);
        }
    }

    private boolean upsertArchiveRow(MessageWorkerEvent event) {
        var sql = """
            INSERT INTO archive_message_meta (
              message_id, chat_id, sender_id, client_msg_id, created_at_epoch_ms,
              type, flags, encrypted, storage_byte_length, archived_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (message_id) DO UPDATE SET
              chat_id = EXCLUDED.chat_id,
              sender_id = EXCLUDED.sender_id,
              client_msg_id = EXCLUDED.client_msg_id,
              created_at_epoch_ms = EXCLUDED.created_at_epoch_ms,
              type = EXCLUDED.type,
              flags = EXCLUDED.flags,
              encrypted = EXCLUDED.encrypted,
              storage_byte_length = EXCLUDED.storage_byte_length,
              archived_at = EXCLUDED.archived_at
            """;
        try (var conn = archiveDataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, UUID.fromString(event.messageId()));
            stmt.setObject(2, UUID.fromString(event.chatId()));
            stmt.setObject(3, UUID.fromString(event.senderId()));
            stmt.setString(4, event.clientMsgId());
            if (event.createdAtEpochMs() != null) {
                stmt.setLong(5, event.createdAtEpochMs());
            } else {
                stmt.setObject(5, null);
            }
            stmt.setString(6, event.type());
            stmt.setInt(7, event.flags());
            stmt.setBoolean(8, event.encrypted());
            if (event.storageByteLength() != null) {
                stmt.setInt(9, event.storageByteLength());
            } else {
                stmt.setObject(9, null);
            }
            stmt.setTimestamp(10, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error(workerMessages.format("worker.archiver.upsert_failed", event.messageId()), e);
            return false;
        }
    }

    private void publishDeepArchive(byte[] jsonPayload, String messageId) {
        try {
            connection.publish(NatsSubjects.MSG_EVENT_DEEP_ARCHIVE, jsonPayload);
            log.debug(workerMessages.format("worker.archiver.deep_handoff_published", messageId));
        } catch (Exception e) {
            log.error(workerMessages.format("worker.common.publish_failed", NatsSubjects.MSG_EVENT_DEEP_ARCHIVE, messageId), e);
        }
    }

    public void shutdown() {
        try {
            connection.close();
        } catch (Exception e) {
            log.warn(workerMessages.get("worker.common.nats_close_error"), e);
        }
        if (archiveDataSource instanceof HikariDataSource h) {
            h.close();
        }
    }

    public static void main(String[] args) {
        var workerMessages = WorkerMessageSources.forWorker(
            ArchiverWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_archiver");
        log.info(workerMessages.format("worker.common.locale", workerMessages.locale()));
        var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        var archiveUrl = System.getenv("ARCHIVE_JDBC_URL");
        DataSource archiveDs = null;
        boolean archiveEnabled = archiveUrl != null && !archiveUrl.isBlank();
        if (archiveEnabled) {
            var user = System.getenv().getOrDefault("ARCHIVE_DB_USER", "avandocmsg");
            var password = System.getenv().getOrDefault("ARCHIVE_DB_PASSWORD", "avandocmsg");
            var config = new HikariConfig();
            config.setJdbcUrl(archiveUrl);
            config.setUsername(user);
            config.setPassword(password);
            config.setMaximumPoolSize(5);
            archiveDs = new HikariDataSource(config);
        }

        try {
            DefaultExports.initialize();
            ArchiverMetricsHttpServer metricsServer = null;
            var metricsPort = parseMetricsPort(System.getenv("ARCHIVER_METRICS_PORT"));
            var worker = new ArchiverWorker(natsUrl, archiveDs, archiveEnabled, workerMessages);
            if (metricsPort > 0) {
                metricsServer = ArchiverMetricsHttpServer.start(metricsPort, worker::healthReady, workerMessages);
                log.info(workerMessages.format("worker.archiver.metrics_url", metricsServer.getPort()));
            }
            worker.start();
            var finalMetricsServer = metricsServer;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                worker.shutdown();
                if (finalMetricsServer != null) {
                    finalMetricsServer.close();
                }
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
}
