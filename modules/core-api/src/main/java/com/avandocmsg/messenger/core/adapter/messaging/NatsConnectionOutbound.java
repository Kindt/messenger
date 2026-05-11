package com.avandocmsg.messenger.core.adapter.messaging;

import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsConnectionStatus;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import io.nats.client.Connection;
import io.nats.client.JetStream;

import java.time.Duration;
import java.util.Optional;

/**
 * Обёртка над NATS/JetStream для {@link NatsOutboundPort}.
 */
public final class NatsConnectionOutbound implements NatsOutboundPort, NatsConnectionStatus {

    private final Connection connection;
    private final Optional<JetStream> jetStream;

    public NatsConnectionOutbound(Connection connection, Optional<JetStream> jetStream) {
        this.connection = connection;
        this.jetStream = jetStream != null ? jetStream : Optional.empty();
    }

    @Override
    public boolean natsClientConnected() {
        return connection != null && connection.getStatus() == Connection.Status.CONNECTED;
    }

    @Override
    public void publish(String subject, byte[] payload) throws Exception {
        if (connection == null) {
            return;
        }
        connection.publish(subject, payload);
    }

    @Override
    public void flush(Duration timeout) throws Exception {
        if (connection == null) {
            return;
        }
        connection.flush(timeout);
    }

    @Override
    public void publishPipelineMessageSend(byte[] payload) throws Exception {
        if (jetStream.isPresent()) {
            jetStream.get().publish(NatsSubjects.MSG_SEND, payload);
            return;
        }
        if (connection == null) {
            return;
        }
        connection.publish(NatsSubjects.MSG_SEND, payload);
        connection.flush(Duration.ofSeconds(1));
    }
}
