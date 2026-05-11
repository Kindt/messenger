package com.avandocmsg.messenger.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AppConfigRetentionWorkerDefaultsTest {

    @Test
    void retentionWorkerDefaults_conservative() {
        assumeTrue(System.getenv("RETENTION_WORKER_ENABLED") == null);
        assumeTrue(System.getenv("RETENTION_SCAN_INTERVAL_SECONDS") == null);
        var cfg = new AppConfig();
        assertFalse(cfg.retentionWorkerEnabled());
        assertEquals(3600, cfg.retentionScanIntervalSeconds());
    }
}
