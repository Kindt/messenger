package com.avandocmsg.messenger.common.nats;

import io.nats.client.Connection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DeliverFanoutTest {

    @Test
    void defaultDirectMaxConstantIs500() {
        assertEquals(500, DeliverFanout.DEFAULT_DIRECT_MAX);
    }

    @Test
    void modeFor_usesDirectWithinThreshold() {
        var cfg = new DeliverFanout.Config(256, true);
        assertEquals(DeliverFanout.Mode.DIRECT, DeliverFanout.modeFor(256, cfg));
        assertEquals(DeliverFanout.Mode.CHAT_BROADCAST, DeliverFanout.modeFor(257, cfg));
    }

    @Test
    void publish_directPublishesPerMember() {
        var nats = mock(Connection.class);
        var cfg = new DeliverFanout.Config(2, true);
        var payload = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        DeliverFanout.publish(nats, List.of("u1", "u2"), "chat-1", payload, cfg);
        verify(nats).publish(NatsSubjects.deliverUserSubject("u1"), payload);
        verify(nats).publish(NatsSubjects.deliverUserSubject("u2"), payload);
        verifyNoMoreInteractions(nats);
    }

    @Test
    void publish_broadcastUsesChatSubject() {
        var nats = mock(Connection.class);
        var cfg = new DeliverFanout.Config(2, true);
        var payload = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        DeliverFanout.publish(nats, List.of("u1", "u2", "u3"), "chat-99", payload, cfg);
        var subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(nats).publish(subjectCaptor.capture(), org.mockito.ArgumentMatchers.eq(payload));
        assertEquals(NatsSubjects.deliverChatSubject("chat-99"), subjectCaptor.getValue());
        verify(nats, times(1)).publish(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publish_dedupSkipsSecondDirectDeliver() {
        var nats = mock(Connection.class);
        var cfg = new DeliverFanout.Config(256, true);
        var dedup = new FanoutDedup(60);
        var payload = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        DeliverFanout.publish(nats, List.of("u1"), "chat-1", payload, cfg, dedup, "msg-dup");
        DeliverFanout.publish(nats, List.of("u1"), "chat-1", payload, cfg, dedup, "msg-dup");
        verify(nats, times(1)).publish(NatsSubjects.deliverUserSubject("u1"), payload);
    }
}
