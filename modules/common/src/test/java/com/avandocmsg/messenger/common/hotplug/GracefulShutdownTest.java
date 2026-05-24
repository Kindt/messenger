package com.avandocmsg.messenger.common.hotplug;

import io.nats.client.Connection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GracefulShutdownTest {

    @Test
    void runShutdown_executesCallbacksAndInvokesDrain() {
        var drainCalls = new AtomicInteger();
        Connection nats = natsWithDrainCounter(drainCalls);
        var beforeCalled = new AtomicBoolean(false);
        var afterCalled = new AtomicBoolean(false);

        GracefulShutdown.runShutdown(
            "indexer-1",
            nats,
            Duration.ofSeconds(1),
            () -> beforeCalled.set(true),
            () -> afterCalled.set(true)
        );

        assertTrue(beforeCalled.get());
        assertTrue(afterCalled.get());
        assertEquals(1, drainCalls.get());
    }

    private static Connection natsWithDrainCounter(AtomicInteger drainCalls) {
        InvocationHandler h = (proxy, method, args) -> {
            if ("drain".equals(method.getName())) {
                drainCalls.incrementAndGet();
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            if ("isClosed".equals(method.getName())) {
                return false;
            }
            return null;
        };
        return (Connection) Proxy.newProxyInstance(
            GracefulShutdownTest.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            h
        );
    }
}
