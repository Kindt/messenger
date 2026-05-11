package com.avandocmsg.messenger.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ensures rate-limit defaults stay predictable for ops / CHANGELOG.
 * Skipped when env overrides are present (local developer machines).
 */
class AppConfigRateLimitDefaultsTest {

    @Test
    void rateLimitDefaults_disabledAndBounded() {
        assumeTrue(System.getenv("RATE_LIMIT_AUTH_ENABLED") == null);
        assumeTrue(System.getenv("RATE_LIMIT_LOGIN_PER_MINUTE") == null);
        assumeTrue(System.getenv("RATE_LIMIT_REGISTER_PER_HOUR") == null);
        var cfg = new AppConfig();
        assertFalse(cfg.rateLimitAuthEnabled());
        assertEquals(60, cfg.rateLimitLoginMaxPerMinute());
        assertEquals(5, cfg.rateLimitRegisterMaxPerHour());
    }
}
