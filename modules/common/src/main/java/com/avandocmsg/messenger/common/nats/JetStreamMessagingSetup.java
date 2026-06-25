package com.avandocmsg.messenger.common.nats;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

/**
 * Creates or verifies the JetStream stream used for durable ingress ({@link NatsSubjects#MSG_SEND}).
 */
public final class JetStreamMessagingSetup {
    private static final Logger log = LoggerFactory.getLogger(JetStreamMessagingSetup.class);

    public static final String STREAM_MESSAGING = "MESSAGING";
    public static final int DEFAULT_MAX_MESSAGES_PER_SUBJECT = 100_000;
    public static final int DEFAULT_MAX_DELIVER = 5;

    private JetStreamMessagingSetup() {
    }

    public static int maxMessagesPerSubject() {
        return parsePositive(System.getenv("NATS_JS_MAX_MESSAGES_PER_SUBJECT"), DEFAULT_MAX_MESSAGES_PER_SUBJECT);
    }

    public static int maxDeliver() {
        return parsePositive(System.getenv("NATS_JS_MAX_DELIVER"), DEFAULT_MAX_DELIVER);
    }

    /**
     * Ensures stream {@value #STREAM_MESSAGING} exists and captures {@link NatsSubjects#MSG_SEND} (+ DLQ).
     */
    public static void ensureSendStream(Connection nc) throws IOException, JetStreamApiException {
        JetStreamManagement jsm = nc.jetStreamManagement();
        var maxPerSubject = maxMessagesPerSubject();
        var subjects = new String[] {NatsSubjects.MSG_SEND, NatsSubjects.MSG_SEND_DLQ};
        try {
            var info = jsm.getStreamInfo(STREAM_MESSAGING);
            var updated = StreamConfiguration.builder(info.getConfiguration())
                .subjects(subjects)
                .maxMessagesPerSubject(maxPerSubject)
                .build();
            jsm.updateStream(updated);
            log.debug("JetStream stream {} updated (maxMessagesPerSubject={})", STREAM_MESSAGING, maxPerSubject);
            return;
        } catch (JetStreamApiException e) {
            if (e.getErrorCode() != 404) {
                log.warn("Unexpected error checking stream {}: {}", STREAM_MESSAGING, e.getMessage());
            }
        }
        var cfg = StreamConfiguration.builder()
            .name(STREAM_MESSAGING)
            .subjects(subjects)
            .storageType(StorageType.File)
            .duplicateWindow(Duration.ofMinutes(2))
            .maxMessagesPerSubject(maxPerSubject)
            .build();
        try {
            jsm.addStream(cfg);
            log.info("JetStream stream {} created for subjects {} / {}", STREAM_MESSAGING,
                NatsSubjects.MSG_SEND, NatsSubjects.MSG_SEND_DLQ);
        } catch (JetStreamApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("stream name already in use")) {
                log.debug("JetStream stream {} race: already exists", STREAM_MESSAGING);
                return;
            }
            throw e;
        }
    }

    private static int parsePositive(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            var parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
