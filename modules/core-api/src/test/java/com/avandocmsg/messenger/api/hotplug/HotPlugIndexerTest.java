package com.avandocmsg.messenger.api.hotplug;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.application.IndexerEventPublisher;
import com.avandocmsg.messenger.core.application.MessageEditCoordinator;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HotPlugIndexerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void queuedIndexEventsAreFlushedAfterIndexerReconnect() throws Exception {
        UUID chatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        Instant now = Instant.now();

        MessageRepositoryPort messageRepositoryPort = mock(MessageRepositoryPort.class);
        NatsOutboundPort natsOutbound = mock(NatsOutboundPort.class);

        var available = new AtomicBoolean(false);
        var indexerPublisher = new IndexerEventPublisher(natsOutbound, available::get);
        var coordinator = new MessageEditCoordinator(messageRepositoryPort, indexerPublisher);

        var edited1 = domainMessage(msgId, chatId, userId, "edited-1", now);
        var edited2 = domainMessage(msgId, chatId, userId, "edited-2", now);

        when(messageRepositoryPort.updateContent(eq(MessageId.of(msgId)), eq(UserId.of(userId)), any(String.class)))
            .thenReturn(true);
        when(messageRepositoryPort.findById(MessageId.of(msgId)))
            .thenReturn(Optional.of(edited1), Optional.of(edited2));
        doNothing().when(natsOutbound).publish(eq(NatsSubjects.MSG_EVENT_INDEX), any(byte[].class));

        var first = coordinator.edit(MessageId.of(msgId), UserId.of(userId), "edited-1");
        assertNotNull(first);
        assertEquals("edited-1", first.content());

        available.set(true);
        var second = coordinator.edit(MessageId.of(msgId), UserId.of(userId), "edited-2");
        assertNotNull(second);
        assertEquals("edited-2", second.content());

        var payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        org.mockito.Mockito.verify(natsOutbound, org.mockito.Mockito.times(2))
            .publish(eq(NatsSubjects.MSG_EVENT_INDEX), payloadCaptor.capture());

        var payloads = payloadCaptor.getAllValues();
        var replayed = MAPPER.readValue(payloads.get(0), MessageWorkerEvent.class);
        var current = MAPPER.readValue(payloads.get(1), MessageWorkerEvent.class);

        assertEquals("update", replayed.indexOp());
        assertEquals("edited-1", replayed.searchText());
        assertEquals(msgId.toString(), replayed.messageId());

        assertEquals("update", current.indexOp());
        assertEquals("edited-2", current.searchText());
        assertEquals(msgId.toString(), current.messageId());
        assertTrue(payloads.size() >= 2);
    }

    private static Message domainMessage(UUID msgId, UUID chatId, UUID userId, String content, Instant now) {
        return new Message(
            MessageId.of(msgId),
            ChatId.of(chatId),
            UserId.of(userId),
            "text",
            content,
            null,
            false,
            now,
            null,
            null,
            null);
    }
}
