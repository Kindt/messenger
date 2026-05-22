package com.avandocmsg.messenger.worker.push;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PushPlatformDefaultsTest {

    @Test
    void metricsPort_defaultsTo9191WhenUnset() {
        assumeTrue(System.getenv("PUSH_METRICS_PORT") == null);
        assertEquals(9191, PushPlatformDefaults.metricsPort());
    }
}
