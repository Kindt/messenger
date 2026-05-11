package com.avandocmsg.messenger.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;

public class HotReloadWatcher {
    private static final Logger log = LoggerFactory.getLogger(HotReloadWatcher.class);
    private final Path libDir;
    private final Runnable onReload;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread watchThread;

    public HotReloadWatcher(Path libDir, Runnable onReload) {
        this.libDir = libDir;
        this.onReload = onReload;
    }

    public void start() {
        if (running.getAndSet(true)) return;
        watchThread = new Thread(this::run, "hot-reload-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
        log.info("HotReloadWatcher watching {}", libDir);
    }

    private void run() {
        try {
            var watchService = FileSystems.getDefault().newWatchService();
            libDir.register(watchService, ENTRY_MODIFY, ENTRY_CREATE);

            while (running.get()) {
                var key = watchService.poll(2, java.util.concurrent.TimeUnit.SECONDS);
                if (key == null) continue;

                for (var event : key.pollEvents()) {
                    var path = (Path) event.context();
                    if (path.toString().endsWith(".jar")) {
                        log.info("JAR changed: {} — triggering reload", path);
                        onReload.run();
                        break;
                    }
                }
                key.reset();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("HotReloadWatcher failed", e);
        }
    }

    public void stop() {
        running.set(false);
        if (watchThread != null) watchThread.interrupt();
    }
}
