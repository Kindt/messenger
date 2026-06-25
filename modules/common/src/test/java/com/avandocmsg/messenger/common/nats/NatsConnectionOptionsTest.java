package com.avandocmsg.messenger.common.nats;

import io.nats.client.Options;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NatsConnectionOptionsTest {

    @Test
    void clientBuilder_setsReconnectPolicy() {
        var options = NatsConnectionOptions.clientBuilder("nats://localhost:4222", "test-client").build();

        assertEquals(URI.create("nats://localhost:4222"), options.getServers().getFirst());
        assertEquals("test-client", options.getConnectionName());
        assertEquals(Duration.ofSeconds(2), options.getReconnectWait());
        assertEquals(-1, options.getMaxReconnect());
        assertEquals(Options.DEFAULT_RECONNECT_JITTER, options.getReconnectJitter());
    }
}
