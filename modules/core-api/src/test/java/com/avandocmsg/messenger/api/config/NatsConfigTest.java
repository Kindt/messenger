package com.avandocmsg.messenger.api.config;

import com.avandocmsg.messenger.common.nats.NatsClientSettings;
import com.avandocmsg.messenger.common.nats.NatsConnectionOptions;
import io.nats.client.Options;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NatsConfigTest {

    @Test
    void natsClientSettings_defaultsFromAppConfig() {
        assumeTrue(System.getenv("NATS_RECONNECT_WAIT_MS") == null);
        assumeTrue(System.getenv("NATS_CONNECTION_TIMEOUT_MS") == null);
        var settings = NatsConfig.natsClientSettings(new AppConfig());
        assertEquals(Duration.ofSeconds(2), settings.reconnectWait());
        assertEquals(-1, settings.maxReconnects());
        assertEquals(Duration.ofSeconds(5), settings.connectionTimeout());
        assertEquals(Duration.ofMinutes(2), settings.pingInterval());
    }

    @Test
    void clientBuilder_appliesCustomSettings() {
        var settings = new NatsClientSettings(
            Duration.ofSeconds(4),
            10,
            Duration.ofSeconds(8),
            Duration.ofSeconds(90));
        var options = NatsConnectionOptions.clientBuilder("nats://localhost:4222", "core-api-test", settings).build();

        assertEquals(URI.create("nats://localhost:4222"), options.getServers().getFirst());
        assertEquals("core-api-test", options.getConnectionName());
        assertEquals(Duration.ofSeconds(4), options.getReconnectWait());
        assertEquals(10, options.getMaxReconnect());
        assertEquals(Duration.ofSeconds(8), options.getConnectionTimeout());
        assertEquals(Duration.ofSeconds(90), options.getPingInterval());
        assertEquals(Options.DEFAULT_RECONNECT_JITTER, options.getReconnectJitter());
    }
}
