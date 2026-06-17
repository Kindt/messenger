package com.avandocmsg.messenger.ws;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

/** WebSocket gateway metrics (PS-2.4). */
public final class WsGatewayMetrics {

    private static final Gauge OPEN_SESSIONS = Gauge.build()
        .name("ws_open_sessions")
        .help("Open WebSocket sessions")
        .register();

    private static final Counter DELIVER_BYTES = Counter.build()
        .name("ws_deliver_bytes_total")
        .help("Bytes delivered to WebSocket clients via NATS fan-out")
        .register();

    private WsGatewayMetrics() {
    }

    public static void setOpenSessions(int count) {
        OPEN_SESSIONS.set(Math.max(0, count));
    }

    public static void addDeliveredBytes(long bytes) {
        if (bytes > 0) {
            DELIVER_BYTES.inc(bytes);
        }
    }
}
