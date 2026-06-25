package com.avandocmsg.messenger.common.nats;

import com.avandocmsg.messenger.common.dto.MessageSendEvent;
import com.avandocmsg.messenger.common.json.MessengerJson;
import io.nats.client.Connection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class MessageDownstreamPublisherTest {

    private static final MessageSendEvent SEND = new MessageSendEvent(
        "msg-1", "chat-1", "user-1", "text", "hello", "client-1", 1_700_000_000_000L, null, null, null, null);

    @Test
    void publish_downstreamOnly_whenLegacyDisabled() throws Exception {
        var nats = mock(Connection.class);
        var mapper = MessengerJson.mapper();

        MessageDownstreamPublisher.publish(nats, SEND, mapper, new MessageDownstreamPublisher.Config(false));

        verify(nats).publish(eq(NatsSubjects.MSG_EVENT_DOWNSTREAM), any(byte[].class));
        verifyNoMoreInteractions(nats);
    }

    @Test
    void publish_dualPublish_whenLegacyEnabled() throws Exception {
        var nats = mock(Connection.class);
        var mapper = MessengerJson.mapper();
        var subjectCaptor = ArgumentCaptor.forClass(String.class);

        MessageDownstreamPublisher.publish(nats, SEND, mapper, new MessageDownstreamPublisher.Config(true));

        verify(nats, times(4)).publish(subjectCaptor.capture(), any(byte[].class));
        var subjects = subjectCaptor.getAllValues();
        assertEquals(NatsSubjects.MSG_EVENT_DOWNSTREAM, subjects.get(0));
        assertEquals(NatsSubjects.MSG_EVENT_INDEX, subjects.get(1));
        assertEquals(NatsSubjects.MSG_EVENT_PUSH, subjects.get(2));
        assertEquals(NatsSubjects.MSG_EVENT_BOT, subjects.get(3));
    }
}
