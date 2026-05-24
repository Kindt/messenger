package com.avandocmsg.messenger.api.hotplug;

import com.avandocmsg.messenger.api.messages.MessageService;
import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.repository.BlockRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
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

        MessageRepository messageRepository = mock(MessageRepository.class);
        ChatRepository chatRepository = mock(ChatRepository.class);
        BlockRepository blockRepository = mock(BlockRepository.class);
        com.avandocmsg.messenger.api.mls.MlsService mlsService = mock(com.avandocmsg.messenger.api.mls.MlsService.class);
        NatsOutboundPort natsOutbound = mock(NatsOutboundPort.class);

        var available = new AtomicBoolean(false);
        var service = new MessageService(
            messageRepository,
            chatRepository,
            blockRepository,
            mlsService,
            natsOutbound,
            UuidGenerator.standard(),
            available::get
        );

        var original = new MessageResponse(
            msgId.toString(), chatId.toString(), userId.toString(),
            "text", "original", null, false, now, null, null, null
        );
        var edited1 = new MessageResponse(
            msgId.toString(), chatId.toString(), userId.toString(),
            "text", "edited-1", null, false, now, now, null, null
        );
        var edited2 = new MessageResponse(
            msgId.toString(), chatId.toString(), userId.toString(),
            "text", "edited-2", null, false, now, now, null, null
        );

        when(chatRepository.getMemberRole(chatId, userId)).thenReturn("member");
        when(chatRepository.isMemberBanned(chatId, userId)).thenReturn(false);
        when(chatRepository.getChatType(chatId)).thenReturn(Optional.empty());
        when(messageRepository.findById(msgId)).thenReturn(Optional.of(original), Optional.of(edited1), Optional.of(edited1), Optional.of(edited2));
        when(messageRepository.updateContent(eq(msgId), eq(userId), any(String.class))).thenReturn(true);
        doNothing().when(natsOutbound).publish(eq(NatsSubjects.MSG_EVENT_INDEX), any(byte[].class));

        var first = service.editMessage(chatId, msgId, userId, "edited-1");
        assertNotNull(first);
        assertEquals("edited-1", first.content());

        available.set(true);
        var second = service.editMessage(chatId, msgId, userId, "edited-2");
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
}
