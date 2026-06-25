package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.nats.NatsConnectionOptions;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import io.nats.client.Connection;
import io.nats.client.Nats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** NATS subscriptions for export-replay jobs (queue group + cancel hints + complete publish). */
public final class ExportReplayNatsConsumer implements AutoCloseable {
    public static final String QUEUE_GROUP = "export-replay-workers";
    private static final Logger log = LoggerFactory.getLogger(ExportReplayNatsConsumer.class);

    private final Connection connection;
    private final UserMessageSource workerMessages;

    public ExportReplayNatsConsumer(String natsUrl, UserMessageSource workerMessages) throws Exception {
        this.workerMessages = workerMessages;
        var options = NatsConnectionOptions.clientBuilder(natsUrl, "export-replay-worker").build();
        this.connection = Nats.connect(options);
        log.info(workerMessages.format("worker.common.connected_nats", natsUrl));
    }

    public void subscribe(ExportReplayJobRunner runner) {
        var dispatcher = connection.createDispatcher(runner::handle);
        dispatcher.subscribe(NatsSubjects.MSG_EXPORT_REPLAY, QUEUE_GROUP);
        dispatcher.subscribe(NatsSubjects.MSG_EXPORT_REPLAY_CANCEL, QUEUE_GROUP, runner::onCancelHint);
    }

    public void publish(String subject, byte[] payload) throws IOException {
        connection.publish(subject, payload);
    }

    public boolean connected() {
        return connection.getStatus() == Connection.Status.CONNECTED;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (Exception e) {
            log.warn(workerMessages.get("worker.common.nats_close_error"), e);
        }
    }
}
