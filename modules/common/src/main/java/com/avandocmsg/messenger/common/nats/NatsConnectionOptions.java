package com.avandocmsg.messenger.common.nats;

import io.nats.client.Options;

/** Shared jnats {@link Options} for long-running API and worker processes. */
public final class NatsConnectionOptions {

    private NatsConnectionOptions() {
    }

    /**
     * Builder with unlimited reconnects, fixed wait, and jitter (FR-049, FR-093).
     */
    public static Options.Builder clientBuilder(String serverUrl, String connectionName) {
        return clientBuilder(serverUrl, connectionName, NatsClientSettings.fromEnv());
    }

    /**
     * Builder with explicit tuning (spec 025 FR-028).
     */
    public static Options.Builder clientBuilder(String serverUrl, String connectionName, NatsClientSettings settings) {
        return Options.builder()
            .server(serverUrl)
            .connectionName(connectionName)
            .reconnectWait(settings.reconnectWait())
            .maxReconnects(settings.maxReconnects())
            .reconnectJitter(Options.DEFAULT_RECONNECT_JITTER)
            .connectionTimeout(settings.connectionTimeout())
            .pingInterval(settings.pingInterval());
    }
}
