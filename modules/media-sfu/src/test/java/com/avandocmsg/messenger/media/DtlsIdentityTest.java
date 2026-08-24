package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DtlsIdentityTest {

    @Test
    void generatesEphemeralEcCertificateAndSha256Fingerprint() throws Exception {
        var now = Instant.parse("2026-08-24T00:00:00Z");

        var identity = DtlsIdentity.generate(Clock.fixed(now, ZoneOffset.UTC), new SecureRandom());

        identity.certificate().checkValidity(java.util.Date.from(now));
        assertEquals("EC", identity.keyPair().getPrivate().getAlgorithm());
        assertTrue(identity.sha256Fingerprint().matches("([0-9A-F]{2}:){31}[0-9A-F]{2}"));
        assertTrue(identity.certificate().getSubjectX500Principal().getName().contains("Korus Media"));
    }
}
