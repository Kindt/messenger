package com.avandocmsg.messenger.common.hotplug;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotPlugHeartbeatTest {

    @Test
    void publish_sendsHeartbeatPayloadToExpectedSubject() {
        var capturedSubject = new AtomicReference<String>();
        var capturedPayload = new AtomicReference<byte[]>();
        Connection nats = natsPublishCapture(capturedSubject, capturedPayload);

        var heartbeat = new HotPlugHeartbeat(
            nats,
            "indexer-1",
            1000,
            Clock.fixed(Instant.parse("2026-05-23T19:00:00Z"), ZoneOffset.UTC),
            new ObjectMapper()
        );
        heartbeat.publish("ACTIVE");

        assertEquals("$SVC.heartbeat.indexer-1", capturedSubject.get());
        var parsed = heartbeat.parse(capturedPayload.get());
        assertEquals("indexer-1", parsed.serviceId());
        assertEquals("ACTIVE", parsed.state());
        assertTrue(parsed.uptimeMs() >= 0);
    }

    private static Connection natsPublishCapture(
        AtomicReference<String> subject,
        AtomicReference<byte[]> payload
    ) {
        InvocationHandler h = (proxy, method, args) -> {
            if ("publish".equals(method.getName()) && args != null && args.length >= 2) {
                if (args[0] instanceof String s && args[1] instanceof byte[] p) {
                    subject.set(s);
                    payload.set(p);
                }
                return null;
            }
            if ("drain".equals(method.getName())) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
            if ("isClosed".equals(method.getName())) {
                return false;
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            return null;
        };
        return (Connection) Proxy.newProxyInstance(
            HotPlugHeartbeatTest.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            h
        );
    }
}
