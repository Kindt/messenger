package com.avandocmsg.messenger.common.http;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class HttpClientSupportTest {

    @Test
    void sharedClient_returnsSingleton() {
        assertSame(HttpClientSupport.sharedClient(), HttpClientSupport.sharedClient());
    }

    @Test
    void sharedFollowingRedirects_returnsSingleton() {
        assertSame(HttpClientSupport.sharedFollowingRedirects(), HttpClientSupport.sharedFollowingRedirects());
    }

    @Test
    void sharedClients_useDistinctPools() {
        assertNotSame(HttpClientSupport.sharedClient(), HttpClientSupport.sharedFollowingRedirects());
    }

    @Test
    void clientBuilder_usesDefaultConnectTimeout() {
        var client = HttpClientSupport.clientBuilder().build();

        assertEquals(HttpClientSupport.DEFAULT_CONNECT_TIMEOUT, client.connectTimeout().orElseThrow());
    }

    @Test
    void clientBuilder_acceptsCustomConnectTimeout() {
        var timeout = Duration.ofSeconds(5);
        var client = HttpClientSupport.clientBuilder(timeout).build();

        assertEquals(timeout, client.connectTimeout().orElseThrow());
    }
}
