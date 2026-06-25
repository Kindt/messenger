package com.avandocmsg.messenger.worker.push;

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
 * FR-162: push consumer deserializes downstream envelope without NATS broker.
 */
class PushNatsContractTest {

    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final String ROUTE_TOKEN = MessageDownstreamRouting.ROUTE_PUSH;

    @Test
    void downstreamEnvelope_deserializesWhenRouteContainsPush() throws Exception {
        var envelope = deserializeDownstreamSample();

        assertTrue(envelope.route().contains(ROUTE_TOKEN));
        assertEquals("msg-1", envelope.payload().messageId());
        assertEquals("hello", envelope.payload().searchText());
    }

    @Test
    void downstreamEnvelope_skipsWhenRouteOmitsPush() throws Exception {
        var worker = MessageWorkerEvent.fromSendEvent(sampleSend());
        var envelope = MAPPER.readValue(
            MAPPER.writeValueAsBytes(new MessageDownstreamEvent(List.of("index", "bot"), "msg-1", "chat-1", worker)),
            MessageDownstreamEvent.class);

        assertFalse(envelope.route().contains(ROUTE_TOKEN));
    }

    @Test
    void dispatchDownstream_truncatesSearchTextForPush() throws Exception {
        var longText = "p".repeat(200);
        var send = new MessageSendEvent(
            "msg-1", "chat-1", "user-1", "text", longText, "client-1", 1_700_000_000_000L, null, null, null, null);
        var worker = MessageWorkerEvent.fromSendEvent(send);
        var bytes = MAPPER.writeValueAsBytes(
            new MessageDownstreamEvent(List.of(ROUTE_TOKEN), "msg-1", "chat-1", worker));
        var msg = mockMessage(NatsSubjects.MSG_EVENT_DOWNSTREAM, bytes);
        var received = new ArrayList<MessageWorkerEvent>();

        MessageDownstreamRouting.dispatchDownstreamMessage(msg, ROUTE_TOKEN, MAPPER, received::add);

        assertEquals(1, received.size());
        assertEquals(MessageWorkerEvent.PUSH_BOT_SEARCH_TEXT_MAX, received.get(0).searchText().length());
    }

    @Test
    void dispatchDownstream_skipsWhenRouteOmitsPush() throws Exception {
        var worker = MessageWorkerEvent.fromSendEvent(sampleSend());
        var bytes = MAPPER.writeValueAsBytes(
            new MessageDownstreamEvent(List.of("index", "bot"), "msg-1", "chat-1", worker));
        var msg = mockMessage(NatsSubjects.MSG_EVENT_DOWNSTREAM, bytes);
        var received = new ArrayList<MessageWorkerEvent>();

        MessageDownstreamRouting.dispatchDownstreamMessage(msg, ROUTE_TOKEN, MAPPER, received::add);

        assertTrue(received.isEmpty());
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
