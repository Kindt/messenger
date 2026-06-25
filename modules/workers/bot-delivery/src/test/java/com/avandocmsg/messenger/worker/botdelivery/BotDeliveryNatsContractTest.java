package com.avandocmsg.messenger.worker.botdelivery;

import com.avandocmsg.messenger.common.dto.MessageDownstreamEvent;
import com.avandocmsg.messenger.common.dto.MessageSendEvent;
import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.nats.MessageDownstreamRouting;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Message;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * FR-162: bot-delivery consumer deserializes downstream envelope without NATS broker.
 */
class BotDeliveryNatsContractTest {

    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final String ROUTE_TOKEN = MessageDownstreamRouting.ROUTE_BOT;

    @Test
    void downstreamEnvelope_deserializesWhenRouteContainsBot() throws Exception {
        var envelope = deserializeDownstreamSample();

        assertTrue(envelope.route().contains(ROUTE_TOKEN));
        assertEquals("msg-1", envelope.payload().messageId());
        assertEquals("hello", envelope.payload().searchText());
    }

    @Test
    void downstreamEnvelope_skipsWhenRouteOmitsBot() throws Exception {
        var worker = MessageWorkerEvent.fromSendEvent(sampleSend());
        var envelope = MAPPER.readValue(
            MAPPER.writeValueAsBytes(new MessageDownstreamEvent(List.of("index", "push"), "msg-1", "chat-1", worker)),
            MessageDownstreamEvent.class);

        assertFalse(envelope.route().contains(ROUTE_TOKEN));
    }

    @Test
    void dispatchDownstream_deliversPayloadWhenRouteMatches() throws Exception {
        var worker = MessageWorkerEvent.fromSendEvent(sampleSend());
        var bytes = MAPPER.writeValueAsBytes(
            new MessageDownstreamEvent(List.of(ROUTE_TOKEN), "msg-1", "chat-1", worker));
        var msg = mockMessage(NatsSubjects.MSG_EVENT_DOWNSTREAM, bytes);
        var received = new ArrayList<MessageWorkerEvent>();

        MessageDownstreamRouting.dispatchDownstreamMessage(msg, ROUTE_TOKEN, MAPPER, received::add);

        assertEquals(1, received.size());
        assertEquals("msg-1", received.get(0).messageId());
    }

    @Test
    void dispatchDownstream_unknownRouteTokensDoNotFail() throws Exception {
        var worker = MessageWorkerEvent.fromSendEvent(sampleSend());
        var bytes = MAPPER.writeValueAsBytes(new MessageDownstreamEvent(
            List.of("index", "push", "bot", "future-consumer"), "msg-1", "chat-1", worker));
        var msg = mockMessage(NatsSubjects.MSG_EVENT_DOWNSTREAM, bytes);
        var received = new ArrayList<MessageWorkerEvent>();

        MessageDownstreamRouting.dispatchDownstreamMessage(msg, ROUTE_TOKEN, MAPPER, received::add);

        assertEquals(1, received.size());
    }

    private static Message mockMessage(String subject, byte[] data) {
        var msg = Mockito.mock(Message.class);
        when(msg.getSubject()).thenReturn(subject);
        when(msg.getData()).thenReturn(data);
        return msg;
    }

    private static MessageDownstreamEvent deserializeDownstreamSample() throws Exception {
        var worker = MessageWorkerEvent.fromSendEvent(sampleSend());
        var bytes = MAPPER.writeValueAsBytes(
            new MessageDownstreamEvent(List.of("index", "push", "bot"), "msg-1", "chat-1", worker));
        return MAPPER.readValue(bytes, MessageDownstreamEvent.class);
    }

    private static MessageSendEvent sampleSend() {
        return new MessageSendEvent(
            "msg-1", "chat-1", "user-1", "text", "hello", "client-1", 1_700_000_000_000L, null, null, null, null);
    }
}
