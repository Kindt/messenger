package com.avandocmsg.messenger.ws;

import com.avandocmsg.messenger.common.nats.NatsSubjects;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WsNatsDeliveryHubTest {

    @Test
    void onMessage_deliversChatBroadcastToRegisteredMembers() throws Exception {
        var registry = new WsSessionRegistry(5, 100);
        var chatId = "chat-1";
        var userA = "user-a";
        var userB = "user-b";
        var sessionA = mock(Session.class);
        var sessionB = mock(Session.class);
        var remoteA = mock(RemoteEndpoint.Basic.class);
        var remoteB = mock(RemoteEndpoint.Basic.class);
        when(sessionA.isOpen()).thenReturn(true);
        when(sessionB.isOpen()).thenReturn(true);
        when(sessionA.getBasicRemote()).thenReturn(remoteA);
        when(sessionB.getBasicRemote()).thenReturn(remoteB);
        registry.register(sessionA, userA, List.of(chatId));
        registry.register(sessionB, userB, List.of(chatId));

        var handlerRef = new AtomicReference<MessageHandler>();
        var dispatcher = mock(Dispatcher.class);
        var connection = mock(Connection.class);
        when(connection.createDispatcher(any())).thenAnswer(invocation -> {
            handlerRef.set(invocation.getArgument(0));
            return dispatcher;
        });
        new WsNatsDeliveryHub(connection, registry);

        var natsMsg = mock(Message.class);
        when(natsMsg.getSubject()).thenReturn(NatsSubjects.deliverChatSubject(chatId));
        when(natsMsg.getData()).thenReturn("{\"event\":\"broadcast\"}".getBytes(StandardCharsets.UTF_8));
        handlerRef.get().onMessage(natsMsg);

        verify(remoteA).sendText("{\"event\":\"broadcast\"}");
        verify(remoteB).sendText("{\"event\":\"broadcast\"}");

        var subscribeCaptor = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).subscribe(subscribeCaptor.capture());
        assertEquals(WsNatsDeliveryHub.DELIVER_WILDCARD, subscribeCaptor.getValue());
    }
}
