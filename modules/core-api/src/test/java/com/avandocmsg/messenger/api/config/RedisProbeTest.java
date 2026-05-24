package com.avandocmsg.messenger.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RedisProbeTest {

    @Test
    void pingReturnsFalseWhenRedisUnreachable() {
        var cfg = new AppConfig() {
            @Override
            public String redisUri() {
                return "redis://127.0.0.1:63999/0";
            }
        };
        var probe = new RedisProbe(cfg, null);
        try {
            assertFalse(probe.ping());
        } finally {
            probe.shutdown();
        }
    }
}
