package com.avandocmsg.messenger.core.port;

import java.time.Duration;

/**
 * Исходящие публикации в NATS без привязки к {@link io.nats.client.Connection} в прикладном коде.
 */
public interface NatsOutboundPort {

    void publish(String subject, byte[] payload) throws Exception; // NOSONAR java:S112 -- port boundary mirrors NATS IO failures

    void flush(Duration timeout) throws Exception; // NOSONAR java:S112 -- port boundary mirrors NATS IO failures

    /**
     * Событие конвейера новых сообщений: JetStream при включённом {@code nats.jetstream},
     * иначе ядро NATS с {@link #flush(Duration)}.
     */
    default void publishPipelineMessageSend(byte[] payload) throws Exception { // NOSONAR java:S112
        publishPipelineMessageSend(payload, null);
    }

    void publishPipelineMessageSend(byte[] payload, String userId) throws Exception; // NOSONAR java:S112 -- port boundary

    static NatsOutboundPort noop() {
        return new NatsOutboundPort() {
            @Override
            public void publish(String subject, byte[] payload) {
                // no-op test/lab double: discard outbound NATS publish
            }

            @Override
            public void flush(Duration timeout) {
                // no-op test/lab double: nothing to flush
            }

            @Override
            public void publishPipelineMessageSend(byte[] payload, String userId) {
                // no-op test/lab double: discard pipeline publish
            }
        };
    }
}
