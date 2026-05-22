package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.repository.ExportJobRepository;
import com.avandocmsg.messenger.common.dto.ExportReplayCompleteEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportReplayCompleteSubscriberTest {

    static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void onMessage_appliesCompleteWhenPending() throws Exception {
        var jobId = UUID.randomUUID();
        var repo = mock(ExportJobRepository.class);
        when(repo.applyCompleteIfPending(jobId, "export_v1", "/export/x.json", true)).thenReturn(true);

        var connection = mock(Connection.class);
        var dispatcher = mock(Dispatcher.class);
        var handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        when(connection.createDispatcher(handlerCaptor.capture())).thenReturn(dispatcher);

        new ExportReplayCompleteSubscriber(connection, repo);

        var event = new ExportReplayCompleteEvent(jobId.toString(), UUID.randomUUID().toString(), "export_v1",
            "/export/x.json", true);
        var msg = mock(Message.class);
        when(msg.getData()).thenReturn(MAPPER.writeValueAsBytes(event));
        handlerCaptor.getValue().onMessage(msg);

        verify(repo).applyCompleteIfPending(jobId, "export_v1", "/export/x.json", true);
    }

    @Test
    void onMessage_ignoresInvalidJobId() throws Exception {
        var repo = mock(ExportJobRepository.class);
        var connection = mock(Connection.class);
        var dispatcher = mock(Dispatcher.class);
        var handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        when(connection.createDispatcher(handlerCaptor.capture())).thenReturn(dispatcher);

        new ExportReplayCompleteSubscriber(connection, repo);

        var msg = mock(Message.class);
        when(msg.getData()).thenReturn("{\"jobId\":\"not-uuid\",\"status\":\"export_v1\"}".getBytes(StandardCharsets.UTF_8));
        handlerCaptor.getValue().onMessage(msg);

        verify(repo, never()).applyCompleteIfPending(any(), any(), any(), any());
    }
}
