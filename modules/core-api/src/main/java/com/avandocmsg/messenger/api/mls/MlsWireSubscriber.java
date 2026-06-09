package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.common.nats.NatsSubjects;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribes to {@code mls.welcome}, {@code mls.commit}, {@code mls.epoch} and applies KMLS wire payloads
 * via {@link MlsWireHandler} (complements {@link MlsWirePublisher}).
 */
public final class MlsWireSubscriber implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MlsWireSubscriber.class);
    static final String QUEUE_GROUP = "core-api-mls-wire";

    private final MlsWireHandler handler;
    private final Dispatcher dispatcher;

    public MlsWireSubscriber(Connection connection, MlsWireHandler handler) {
        this.handler = handler;
        this.dispatcher = connection.createDispatcher(msg -> handler.handle(msg.getSubject(), msg.getData()));
    }

    public void start() {
        dispatcher.subscribe(NatsSubjects.MLS_WELCOME, QUEUE_GROUP);
        dispatcher.subscribe(NatsSubjects.MLS_COMMIT, QUEUE_GROUP);
        dispatcher.subscribe(NatsSubjects.MLS_EPOCH, QUEUE_GROUP);
        log.info("Subscribed to {}, {}, {} (queue: {})",
            NatsSubjects.MLS_WELCOME, NatsSubjects.MLS_COMMIT, NatsSubjects.MLS_EPOCH, QUEUE_GROUP);
    }

    @Override
    public void close() {
        for (var subject : new String[]{
            NatsSubjects.MLS_WELCOME, NatsSubjects.MLS_COMMIT, NatsSubjects.MLS_EPOCH}) {
            try {
                dispatcher.unsubscribe(subject);
            } catch (Exception e) {
                log.debug("Unsubscribe {}: {}", subject, e.getMessage());
            }
        }
    }
}
