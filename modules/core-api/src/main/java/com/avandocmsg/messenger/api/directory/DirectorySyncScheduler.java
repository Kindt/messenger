package com.avandocmsg.messenger.api.directory;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.common.scheduling.ScheduledTaskSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Periodic LDAP directory sync for orgs with enabled LDAP auth provider. */
public final class DirectorySyncScheduler implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DirectorySyncScheduler.class);

    private final DirectorySyncService directorySyncService;
    private final ScheduledExecutorService executor;

    public DirectorySyncScheduler(AppConfig appConfig, DirectorySyncService directorySyncService) {
        this.directorySyncService = directorySyncService;
        var minutes = appConfig.directorySyncIntervalMinutes();
        if (minutes <= 0) {
            this.executor = null;
            log.info("Directory sync scheduler disabled (DIRECTORY_SYNC_INTERVAL_MINUTES=0)");
            return;
        }
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "directory-sync");
            t.setDaemon(true);
            return t;
        });
        var initialDelay = Math.min(minutes, 5L);
        ScheduledTaskSupport.scheduleAtFixedRateWithJitter(
            executor, this::tick, initialDelay, minutes, TimeUnit.MINUTES.toMillis(2), TimeUnit.MINUTES);
        log.info("Directory sync scheduler started (interval {} min)", minutes);
    }

    void tick() {
        try {
            directorySyncService.syncAllOrgsWithLdap();
        } catch (Exception e) {
            log.warn("Directory sync tick failed: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
