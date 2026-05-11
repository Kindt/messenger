package com.avandocmsg.messenger.worker.preview;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class SsrfGuardTest {

    @Test
    void parseHttpUri_acceptsHttps() throws Exception {
        URI u = SsrfGuard.parseHttpUri("https://example.com/a");
        assertEquals("example.com", u.getHost());
    }

    @Test
    void parseHttpUri_rejectsNonHttp() {
        assertThrows(Exception.class, () -> SsrfGuard.parseHttpUri("ftp://x/y"));
    }

    @Test
    void isBlocked_loopback() throws Exception {
        assertTrue(SsrfGuard.isBlocked(InetAddress.getByName("127.0.0.1")));
    }

    @Test
    void validateHostAllowed_blocksLocalhostName() {
        assertThrows(Exception.class, () -> SsrfGuard.validateHostAllowed("localhost"));
    }
}
