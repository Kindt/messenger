package com.avandocmsg.messenger.core.port;

import java.time.Duration;

/**
 * Исходящие публикации в NATS без привязки к {@link io.nats.client.Connection} в прикладном коде.
 */
public interface NatsOutboundPort {

    void publish(String subject, byte[] payload) throws Exception;

    void flush(Duration timeout) throws Exception;

    /**
     * Событие конвейера новых сообщений: JetStream при включённом {@code nats.jetstream},
     * иначе ядро NATS с {@link #flush(Duration)}.
     */
    void publishPipelineMessageSend(byte[] payload) throws Exception;

    static NatsOutboundPort noop() {
        return new NatsOutboundPort() {
            @Override
            public void publish(String subject, byte[] payload) {
            }

            @Override
            public void flush(Duration timeout) {
            }

            @Override
            public void publishPipelineMessageSend(byte[] payload) {
            }
        };
    }
}
