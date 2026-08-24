package com.avandocmsg.messenger.api.calls;

import com.avandocmsg.messenger.common.dto.CallSessionEvent;
import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NatsCallSessionEventPublisher implements CallSessionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NatsCallSessionEventPublisher.class);
    private final NatsOutboundPort nats;

    public NatsCallSessionEventPublisher(NatsOutboundPort nats) {
        this.nats = Objects.requireNonNull(nats, "nats");
    }

    @Override
    public void publish(CallSessionEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            nats.publish(
                NatsSubjects.deliverChatSubject(event.chatId()),
                MessengerJson.mapper().writeValueAsBytes(event)
            );
        } catch (Exception error) {
            log.warn("Failed to publish call event {} for session {}", event.type(), event.sessionId(), error);
        }
    }
}
