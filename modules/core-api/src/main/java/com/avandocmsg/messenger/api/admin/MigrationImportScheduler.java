package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.MigrationImportJobPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Background poll for pending migration import jobs (spec 022 T02273 batch). */
public final class MigrationImportScheduler implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MigrationImportScheduler.class);

    private final MigrationImportProcessor processor;
    private final MigrationImportJobPort migrationImportJobPort;
    private final int batchSize;
    private final ScheduledExecutorService executor;

    public MigrationImportScheduler(
        AppConfig appConfig,
        MigrationImportJobPort migrationImportJobPort,
        ChatPersistencePort chatPersistencePort,
        MessageRepositoryPort messageRepositoryPort
    ) {
        this.migrationImportJobPort = migrationImportJobPort;
        this.processor = new MigrationImportProcessor(
            migrationImportJobPort, chatPersistencePort, messageRepositoryPort, null);
        this.batchSize = appConfig.migrationImportBatchSize();
        var seconds = appConfig.migrationImportPollSeconds();
        if (seconds <= 0) {
            this.executor = null;
            log.info("Migration import scheduler disabled (MIGRATION_IMPORT_POLL_SECONDS=0)");
            return;
        }
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "migration-import");
            t.setDaemon(true);
            return t;
        });
        var initialDelay = Math.min(seconds, 30L);
        executor.scheduleAtFixedRate(this::tick, initialDelay, seconds, TimeUnit.SECONDS);
        log.info("Migration import scheduler started (poll {} s, batch {})", seconds, batchSize);
    }

    void tick() {
        try {
            var pending = migrationImportJobPort.listPending(batchSize);
            if (pending.isEmpty()) {
                return;
            }
            log.info("Migration import scheduler processing {} job(s)", pending.size());
            for (var job : pending) {
                processor.process(job.id());
            }
        } catch (Exception e) {
            log.warn("Migration import tick failed: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
