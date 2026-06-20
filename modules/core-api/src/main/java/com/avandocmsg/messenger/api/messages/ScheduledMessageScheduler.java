package com.avandocmsg.messenger.api.messages;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.core.application.MessageSendCoordinator;
import com.avandocmsg.messenger.core.port.ScheduledMessagePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Dispatches due scheduled messages via {@link MessageSendCoordinator}. */
public final class ScheduledMessageScheduler implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ScheduledMessageScheduler.class);

    private final ScheduledMessagePort scheduledMessagePort;
    private final MessageSendCoordinator messageSendCoordinator;
    private final Clock clock;
    private final int batchSize;
    private final ScheduledExecutorService executor;

    public ScheduledMessageScheduler(
        AppConfig appConfig,
        ScheduledMessagePort scheduledMessagePort,
        MessageSendCoordinator messageSendCoordinator,
        Clock clock
    ) {
        this.scheduledMessagePort = scheduledMessagePort;
        this.messageSendCoordinator = messageSendCoordinator;
        this.clock = clock;
        this.batchSize = appConfig.scheduledMessageBatchSize();
        var seconds = appConfig.scheduledMessagePollSeconds();
        if (seconds <= 0) {
            this.executor = null;
            log.info("Scheduled message scheduler disabled (SCHEDULED_MESSAGE_POLL_SECONDS=0)");
            return;
        }
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "scheduled-messages");
            t.setDaemon(true);
            return t;
        });
        var initialDelay = Math.min(seconds, 30L);
        executor.scheduleAtFixedRate(this::tick, initialDelay, seconds, TimeUnit.SECONDS);
        log.info("Scheduled message scheduler started (poll {} s, batch {})", seconds, batchSize);
    }

    void tick() {
        try {
            var due = scheduledMessagePort.listDue(clock.instant(), batchSize);
            if (due.isEmpty()) {
                return;
            }
            log.debug("Scheduled message scheduler processing {} row(s)", due.size());
            for (var row : due) {
                processRow(row);
            }
        } catch (Exception e) {
            log.warn("Scheduled message tick failed: {}", e.getMessage());
        }
    }

    private void processRow(ScheduledMessagePort.ScheduledRow row) {
        try {
            var request = new SendMessageRequest(
                row.messageType(),
                row.content(),
                row.replyToMsgId() != null ? row.replyToMsgId().toString() : null,
                row.threadId() != null ? row.threadId().toString() : null,
                row.clientMsgId(),
                null,
                null,
                null,
                null);
            var sent = messageSendCoordinator.send(
                row.chatId(), row.senderId(), request, row.replyToMsgId());
            if (sent != null && sent.id() != null) {
                scheduledMessagePort.updateStatus(
                    row.id(), "sent", java.util.UUID.fromString(sent.id()));
            } else {
                scheduledMessagePort.updateStatus(row.id(), "failed", null);
            }
        } catch (Exception e) {
            log.warn("Scheduled message send failed id={}: {}", row.id(), e.getMessage());
            scheduledMessagePort.updateStatus(row.id(), "failed", null);
        }
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
