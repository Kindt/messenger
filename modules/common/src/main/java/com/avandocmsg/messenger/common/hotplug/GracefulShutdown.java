package com.avandocmsg.messenger.common.hotplug;

import io.nats.client.Connection;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Utility for graceful service shutdown with NATS drain and hot-plug metrics.
 */
public final class GracefulShutdown {

    private GracefulShutdown() {
    }

    public static Thread register(
        String serviceId,
        Connection nats,
        Duration drainTimeout,
        Runnable beforeDrain,
        Runnable afterDrain
    ) {
        var hook = new Thread(
            () -> runShutdown(serviceId, nats, drainTimeout, beforeDrain, afterDrain),
            "hotplug-graceful-shutdown-" + normalize(serviceId)
        );
        Runtime.getRuntime().addShutdownHook(hook);
        return hook;
    }

    public static void runShutdown(
        String serviceId,
        Connection nats,
        Duration drainTimeout,
        Runnable beforeDrain,
        Runnable afterDrain
    ) {
        var sid = normalize(serviceId);
        var timeout = drainTimeout != null ? drainTimeout : Duration.ofSeconds(10);
        Runnable before = beforeDrain != null ? beforeDrain : () -> { /* optional beforeDrain */ };
        Runnable after = afterDrain != null ? afterDrain : () -> { /* optional afterDrain */ };
        boolean success = true;
        long started = System.nanoTime();
        RuntimeException pending = null;
        try {
            before.run();
            if (nats != null) {
                invokeDrain(nats, timeout);
            }
        } catch (RuntimeException e) {
            success = false;
            pending = e;
        } catch (Exception e) {
            success = false;
            pending = new IllegalStateException("Graceful shutdown drain failed", e);
        }
        try {
            after.run();
        } catch (RuntimeException e) {
            success = false;
            if (pending != null) {
                pending.addSuppressed(e);
            } else {
                pending = e;
            }
        } finally {
            double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
            HotPlugMetrics.observeDrainDurationSeconds(sid, seconds, success);
        }
        if (pending != null) {
            throw pending;
        }
    }

    private static void invokeDrain(Connection nats, Duration timeout) throws Exception {
        Method drainWithTimeout;
        try {
            drainWithTimeout = nats.getClass().getMethod("drain", Duration.class);
        } catch (NoSuchMethodException e) {
            var noArgDrain = nats.getClass().getMethod("drain");
            noArgDrain.invoke(nats);
            return;
        }
        var result = drainWithTimeout.invoke(nats, timeout);
        if (result instanceof java.util.concurrent.Future<?> future) {
            var millis = Math.max(1L, timeout.toMillis());
            future.get(millis, TimeUnit.MILLISECONDS);
        }
    }

    private static String normalize(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return "unknown";
        }
        return serviceId.trim();
    }
}
