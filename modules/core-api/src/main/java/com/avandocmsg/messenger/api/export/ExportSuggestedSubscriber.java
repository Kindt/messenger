package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.dto.ExportSuggestedEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribes to {@link NatsSubjects#MSG_EXPORT_SUGGESTED} and delegates to {@link ExportSuggestedHandler}.
 */
public final class ExportSuggestedSubscriber implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ExportSuggestedSubscriber.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    static final String QUEUE_GROUP = "core-api-export-suggested";

    private final ExportSuggestedHandler handler;
    private final Dispatcher dispatcher;

    public ExportSuggestedSubscriber(Connection connection, ExportSuggestedHandler handler) {
        this.handler = handler;
        this.dispatcher = connection.createDispatcher(this::onMessage);
    }

    public void start() {
        dispatcher.subscribe(NatsSubjects.MSG_EXPORT_SUGGESTED, QUEUE_GROUP);
        log.info("Subscribed to {} (queue: {})", NatsSubjects.MSG_EXPORT_SUGGESTED, QUEUE_GROUP);
    }

    void onMessage(io.nats.client.Message msg) {
        try {
            var event = MAPPER.readValue(msg.getData(), ExportSuggestedEvent.class);
            handler.handle(event);
        } catch (Exception e) {
            log.warn("Failed to handle {}: {}", NatsSubjects.MSG_EXPORT_SUGGESTED, e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            dispatcher.unsubscribe(NatsSubjects.MSG_EXPORT_SUGGESTED);
        } catch (Exception e) {
            log.debug("Unsubscribe {}: {}", NatsSubjects.MSG_EXPORT_SUGGESTED, e.getMessage());
        }
    }
}
