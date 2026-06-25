package com.avandocmsg.messenger.common.nats;

import com.avandocmsg.messenger.common.dto.MessageDownstreamEvent;
import com.avandocmsg.messenger.common.dto.MessageSendEvent;
import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;

import java.util.List;

/**
 * Pipeline downstream fan-out (spec 025 FR-012 Phase 3).
 */
public final class MessageDownstreamPublisher {

    public record Config(boolean legacyPublish) {
        public static Config fromEnv() {
            return new Config(MessageDownstreamRouting.legacyPublishEnabled());
        }
    }

    private MessageDownstreamPublisher() {
    }

    public static void publish(
        Connection nats,
        MessageSendEvent sendEvent,
        ObjectMapper mapper,
        Config config
    ) throws java.io.IOException {
        var workerEvent = MessageWorkerEvent.fromSendEvent(sendEvent);
        var downstream = new MessageDownstreamEvent(
            List.of(
                MessageDownstreamRouting.ROUTE_INDEX,
                MessageDownstreamRouting.ROUTE_PUSH,
                MessageDownstreamRouting.ROUTE_BOT),
            sendEvent.messageId(),
            sendEvent.chatId(),
            workerEvent);
        nats.publish(NatsSubjects.MSG_EVENT_DOWNSTREAM, mapper.writeValueAsBytes(downstream));

        if (config.legacyPublish()) {
            var indexPayload = mapper.writeValueAsBytes(workerEvent);
            var previewEvent = workerEvent.withSearchTextMaxChars(MessageWorkerEvent.PUSH_BOT_SEARCH_TEXT_MAX);
            var previewPayload = mapper.writeValueAsBytes(previewEvent);
            nats.publish(NatsSubjects.MSG_EVENT_INDEX, indexPayload);
            nats.publish(NatsSubjects.MSG_EVENT_PUSH, previewPayload);
            nats.publish(NatsSubjects.MSG_EVENT_BOT, previewPayload);
        }
    }
}
