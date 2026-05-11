package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.dto.ExportReplayCompleteEvent;
import com.avandocmsg.messenger.common.dto.ExportReplayJob;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Export / compliance replay stub: consumes JSON {@link ExportReplayJob} on {@link NatsSubjects#MSG_EXPORT_REPLAY},
 * writes a marker file under {@code EXPORT_DIR}, optionally publishes {@link NatsSubjects#MSG_EXPORT_REPLAY_COMPLETE}.
 */
public class ExportReplayWorker {
    private static final Logger log = LoggerFactory.getLogger(ExportReplayWorker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String QUEUE_GROUP = "export-replay-workers";

    private final Connection connection;
    private final Path exportDir;
    private final boolean publishComplete;

    public ExportReplayWorker(String natsUrl, Path exportDir, boolean publishComplete) throws Exception {
        this.exportDir = exportDir;
        this.publishComplete = publishComplete;
        var options = Options.builder()
            .server(natsUrl)
            .connectionName("export-replay-worker")
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(-1)
            .build();
        this.connection = Nats.connect(options);
        log.info("Connected to NATS at {}", natsUrl);
    }

    public void start() throws Exception {
        Files.createDirectories(exportDir);
        var dispatcher = connection.createDispatcher(this::handle);
        dispatcher.subscribe(NatsSubjects.MSG_EXPORT_REPLAY, QUEUE_GROUP);
        log.info("Subscribed to {} (queue: {}) exportDir={}", NatsSubjects.MSG_EXPORT_REPLAY, QUEUE_GROUP, exportDir);
    }

    private void handle(io.nats.client.Message msg) {
        try {
            var payload = new String(msg.getData(), StandardCharsets.UTF_8);
            var job = MAPPER.readValue(payload, ExportReplayJob.class);
            if (job.jobId() == null || job.jobId().isBlank() || job.chatId() == null || job.chatId().isBlank()) {
                log.warn("Invalid export job payload: {}", payload);
                return;
            }
            var safeJobId = job.jobId().replaceAll("[^a-zA-Z0-9._-]", "_");
            var out = exportDir.resolve(safeJobId + ".export.json");
            var stub = MAPPER.createObjectNode()
                .put("jobId", job.jobId())
                .put("chatId", job.chatId())
                .put("requestedBy", job.requestedBy() != null ? job.requestedBy() : "")
                .put("stubStatus", "pending_implementation")
                .put("writtenAtEpochMs", Instant.now().toEpochMilli());
            Files.writeString(out, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(stub), StandardCharsets.UTF_8);
            log.info("Export replay stub written jobId={} path={}", job.jobId(), out.toAbsolutePath());

            if (publishComplete) {
                var done = new ExportReplayCompleteEvent(job.jobId(), job.chatId(), "stub_written",
                    out.toAbsolutePath().toString());
                connection.publish(NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE,
                    MAPPER.writeValueAsBytes(done));
                log.debug("Published {}", NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE);
            }
        } catch (Exception e) {
            log.error("Failed to handle export-replay message", e);
        }
    }

    public void shutdown() {
        try {
            connection.close();
        } catch (Exception e) {
            log.warn("Error closing NATS connection", e);
        }
    }

    public static void main(String[] args) {
        var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        var dir = Path.of(System.getenv().getOrDefault("EXPORT_DIR", "export-output"));
        var publishComplete = Boolean.parseBoolean(System.getenv().getOrDefault("EXPORT_PUBLISH_COMPLETE", "false"));

        try {
            var worker = new ExportReplayWorker(natsUrl, dir, publishComplete);
            worker.start();
            Runtime.getRuntime().addShutdownHook(new Thread(worker::shutdown));
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("Fatal error", e);
            System.exit(1);
        }
    }
}
