package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcAuditAdapter;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.common.dto.ExportSuggestedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class ExportSuggestedSubscriberTest {

    static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void onMessage_recordsAudit() throws Exception {
        var chatId = UUID.randomUUID();
        var audit = mock(AuditRepository.class);
        var handler = new ExportSuggestedHandler(new JdbcAuditAdapter(audit));
        var connection = mock(Connection.class);
        var dispatcher = mock(Dispatcher.class);
        var handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        when(connection.createDispatcher(handlerCaptor.capture())).thenReturn(dispatcher);

        new ExportSuggestedSubscriber(connection, handler);

        var event = new ExportSuggestedEvent(
            chatId.toString(),
            ExportSuggestedEvent.REASON_HOT_BODY_CANDIDATES,
            3,
            1_700_000_000_000L
        );
        var msg = mock(Message.class);
        when(msg.getData()).thenReturn(MAPPER.writeValueAsBytes(event));
        handlerCaptor.getValue().onMessage(msg);

        var detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(audit).record(
            isNull(),
            eq(ExportSuggestedHandler.AUDIT_ACTION),
            eq(ExportSuggestedHandler.RESOURCE_TYPE_CHAT),
            eq(chatId.toString()),
            detailsCaptor.capture()
        );
        var details = MAPPER.readTree(detailsCaptor.getValue());
        assertEquals(3, details.get("candidate_message_count").asInt());
    }

    @Test
    void onMessage_invokesAutoQueueWhenConfigured() throws Exception {
        var chatId = UUID.randomUUID();
        var audit = mock(AuditRepository.class);
        var autoQueue = mock(ExportAutoQueueOnSuggested.class);
        var handler = new ExportSuggestedHandler(new JdbcAuditAdapter(audit), Optional.of(autoQueue));
        var connection = mock(Connection.class);
        var dispatcher = mock(Dispatcher.class);
        var handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        when(connection.createDispatcher(handlerCaptor.capture())).thenReturn(dispatcher);

        new ExportSuggestedSubscriber(connection, handler);

        var event = new ExportSuggestedEvent(chatId.toString(), "hot_body_candidates", 1, 0L);
        var msg = mock(Message.class);
        when(msg.getData()).thenReturn(MAPPER.writeValueAsBytes(event));
        handlerCaptor.getValue().onMessage(msg);

        verify(autoQueue).tryQueue(event);
    }

    @Test
    void onMessage_skipsAutoQueueWhenAbsent() throws Exception {
        var audit = mock(AuditRepository.class);
        var autoQueue = mock(ExportAutoQueueOnSuggested.class);
        var handler = new ExportSuggestedHandler(new JdbcAuditAdapter(audit));
        var connection = mock(Connection.class);
        var dispatcher = mock(Dispatcher.class);
        var handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        when(connection.createDispatcher(handlerCaptor.capture())).thenReturn(dispatcher);

        new ExportSuggestedSubscriber(connection, handler);

        var msg = mock(Message.class);
        when(msg.getData()).thenReturn(MAPPER.writeValueAsBytes(
            new ExportSuggestedEvent(UUID.randomUUID().toString(), "x", 1, 0L)));
        handlerCaptor.getValue().onMessage(msg);

        verify(autoQueue, never()).tryQueue(org.mockito.ArgumentMatchers.any());
    }
}
