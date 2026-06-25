package com.avandocmsg.messenger.ws;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

/** WebSocket gateway metrics (PS-2.4 / FR-170). */
public final class WsGatewayMetrics {

    private static final Gauge ACTIVE_SESSIONS = Gauge.build()
        .name("ws_active_sessions")
        .help("Active WebSocket sessions")
        .register();

    private static final Counter DELIVER_BYTES = Counter.build()
        .name("ws_deliver_bytes_total")
        .help("Bytes delivered to WebSocket clients via NATS fan-out")
        .register();

    private static final Counter FANOUT_RECIPIENTS = Counter.build()
        .name("ws_fanout_recipients")
        .help("WebSocket fan-out recipient deliveries (session sends)")
        .register();

    private WsGatewayMetrics() {
    }

    public static void setActiveSessions(int count) {
        ACTIVE_SESSIONS.set(Math.max(0, count));
    }

    public static void addDeliveredBytes(long bytes) {
        if (bytes > 0) {
            DELIVER_BYTES.inc(bytes);
        }
    }

    public static void addFanoutRecipients(int recipients) {
        if (recipients > 0) {
            FANOUT_RECIPIENTS.inc(recipients);
        }
    }
}
