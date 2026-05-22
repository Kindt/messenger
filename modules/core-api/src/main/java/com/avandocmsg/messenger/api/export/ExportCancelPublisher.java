package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.common.dto.ExportReplayCancelEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;

/** Publishes {@link NatsSubjects#MSG_EXPORT_REPLAY_CANCEL} after DB cancel succeeds. */
public final class ExportCancelPublisher {

    private static final Logger log = LoggerFactory.getLogger(ExportCancelPublisher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExportCancelPublisher() {}

    public static void publish(NatsOutboundPort nats, UUID jobId, UUID chatId) {
        if (nats == null || jobId == null || chatId == null) {
            return;
        }
        try {
            var event = new ExportReplayCancelEvent(
                jobId.toString(),
                chatId.toString(),
                System.currentTimeMillis());
            nats.publish(NatsSubjects.MSG_EXPORT_REPLAY_CANCEL, MAPPER.writeValueAsBytes(event));
            nats.flush(Duration.ofSeconds(2));
        } catch (Exception e) {
            log.warn("Failed to publish {} for job {}: {}", NatsSubjects.MSG_EXPORT_REPLAY_CANCEL, jobId, e.getMessage());
        }
    }
}
