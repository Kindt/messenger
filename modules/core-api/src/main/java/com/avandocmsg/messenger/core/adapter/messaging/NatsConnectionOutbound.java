package com.avandocmsg.messenger.core.adapter.messaging;

import com.avandocmsg.messenger.common.logging.WorkerMdcSupport;
import com.avandocmsg.messenger.common.logging.WorkerNatsMdc;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsConnectionStatus;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import io.nats.client.Connection;
import io.nats.client.impl.Headers;
import io.nats.client.JetStream;
import org.slf4j.MDC;

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
    public void publishPipelineMessageSend(byte[] payload, String userId) throws Exception {
        var headers = pipelineHeaders(userId);
        if (jetStream.isPresent()) {
            jetStream.get().publish(NatsSubjects.MSG_SEND, headers, payload);
            return;
        }
        if (connection == null) {
            return;
        }
        connection.publish(NatsSubjects.MSG_SEND, headers, payload);
        connection.flush(Duration.ofSeconds(1));
    }

    private static Headers pipelineHeaders(String userId) {
        var headers = WorkerNatsMdc.toNatsHeaders();
        if (userId != null && !userId.isBlank()) {
            headers.add(WorkerMdcSupport.USER_ID, userId);
        }
        var requestId = MDC.get(WorkerMdcSupport.X_REQUEST_ID);
        if (requestId != null && !requestId.isBlank() && headers.get(WorkerMdcSupport.X_REQUEST_ID) == null) {
            headers.add(WorkerMdcSupport.X_REQUEST_ID, requestId);
        }
        return headers;
    }
}
