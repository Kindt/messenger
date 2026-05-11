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

    private JetStreamMessagingSetup() {
    }

    /**
     * Ensures stream {@value #STREAM_MESSAGING} exists and captures {@link NatsSubjects#MSG_SEND}.
     */
    public static void ensureSendStream(Connection nc) throws IOException, JetStreamApiException {
        JetStreamManagement jsm = nc.jetStreamManagement();
        try {
            jsm.getStreamInfo(STREAM_MESSAGING);
            log.debug("JetStream stream {} already present", STREAM_MESSAGING);
            return;
        } catch (JetStreamApiException e) {
            if (e.getErrorCode() != 404) {
                log.warn("Unexpected error checking stream {}: {}", STREAM_MESSAGING, e.getMessage());
            }
        }
        var cfg = StreamConfiguration.builder()
            .name(STREAM_MESSAGING)
            .subjects(NatsSubjects.MSG_SEND)
            .storageType(StorageType.File)
            .duplicateWindow(Duration.ofMinutes(2))
            .build();
        try {
            jsm.addStream(cfg);
            log.info("JetStream stream {} created for subject {}", STREAM_MESSAGING, NatsSubjects.MSG_SEND);
        } catch (JetStreamApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("stream name already in use")) {
                log.debug("JetStream stream {} race: already exists", STREAM_MESSAGING);
                return;
            }
            throw e;
        }
    }
}
