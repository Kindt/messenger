package com.avandocmsg.messenger.api.messages;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.MessageReminderPort;
import com.avandocmsg.messenger.common.scheduling.ScheduledTaskSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Marks due message reminders as {@code reminded}. */
public final class MessageReminderScheduler implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MessageReminderScheduler.class);

    private final MessageReminderPort messageReminderPort;
    private final Clock clock;
    private final int batchSize;
    private final ScheduledExecutorService executor;

    public MessageReminderScheduler(
        AppConfig appConfig,
        MessageReminderPort messageReminderPort,
        Clock clock
    ) {
        this.messageReminderPort = messageReminderPort;
        this.clock = clock;
        this.batchSize = appConfig.messageReminderBatchSize();
        var seconds = appConfig.messageReminderPollSeconds();
        if (seconds <= 0) {
            this.executor = null;
            log.info("Message reminder scheduler disabled (MESSAGE_REMINDER_POLL_SECONDS=0)");
            return;
        }
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "message-reminders");
            t.setDaemon(true);
            return t;
        });
        var initialDelay = Math.min(seconds, 30L);
        ScheduledTaskSupport.scheduleAtFixedRateWithJitter(
            executor, this::tick, initialDelay, seconds, 5000L, TimeUnit.SECONDS);
        log.info("Message reminder scheduler started (poll {} s, batch {})", seconds, batchSize);
    }

    void tick() {
        try {
            var due = messageReminderPort.listDue(clock.instant(), batchSize);
            if (due.isEmpty()) {
                return;
            }
            log.debug("Message reminder scheduler processing {} row(s)", due.size());
            for (var row : due) {
                messageReminderPort.updateStatus(row.id(), "reminded");
            }
        } catch (Exception e) {
            log.warn("Message reminder tick failed: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
