package com.avandocmsg.messenger.api.calls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.avandocmsg.messenger.common.dto.CallSessionEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NatsCallSessionEventPublisherTest {

    @Test
    void publishesProviderNeutralEventToAuthenticatedChatDeliverySubject() {
        var nats = new CapturingNats();
        var publisher = new NatsCallSessionEventPublisher(nats);
        var event = new CallSessionEvent(
            CallSessionEvent.INVITED,
            "chat-1",
            "session-1",
            "user-1",
            "video",
            "2026-08-24T00:00:00Z"
        );

        publisher.publish(event);

        assertEquals(NatsSubjects.deliverChatSubject("chat-1"), nats.subject);
        var json = new String(nats.payload, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"type\":\"call.invited\""));
        assertTrue(json.contains("\"media_intent\":\"video\""));
        assertTrue(json.contains("\"session_id\":\"session-1\""));
    }

    private static final class CapturingNats implements NatsOutboundPort {
        private String subject;
        private byte[] payload;

        @Override
        public void publish(String subject, byte[] payload) {
            this.subject = subject;
            this.payload = payload;
        }

        @Override
        public void flush(Duration timeout) {}

        @Override
        public void publishPipelineMessageSend(byte[] payload, String userId) {}
    }
}
