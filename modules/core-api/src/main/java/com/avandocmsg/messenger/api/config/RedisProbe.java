package com.avandocmsg.messenger.api.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * Shared Redis ping for health/admin probes. Reuses {@link RedisConfig} when present;
 * otherwise keeps one lazy Lettuce client instead of creating a client per request.
 */
public final class RedisProbe {

    private final AppConfig appConfig;
    private final RedisConfig sharedRateLimitConfig;
    private volatile RedisClient dedicatedClient;
    private volatile StatefulRedisConnection<String, String> dedicatedConnection;

    public RedisProbe(AppConfig appConfig, RedisConfig sharedRateLimitConfig) {
        this.appConfig = appConfig;
        this.sharedRateLimitConfig = sharedRateLimitConfig;
    }

    public boolean ping() {
        if (sharedRateLimitConfig != null) {
            try {
                return "PONG".equals(sharedRateLimitConfig.sync().ping());
            } catch (Exception e) {
                return false;
            }
        }
        return pingDedicated();
    }

    public void shutdown() {
        var conn = dedicatedConnection;
        var client = dedicatedClient;
        dedicatedConnection = null;
        dedicatedClient = null;
        if (conn != null) {
            conn.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }

    private synchronized boolean pingDedicated() {
        try {
            if (dedicatedClient == null) {
                dedicatedClient = RedisClient.create(RedisURI.create(appConfig.redisUri()));
                dedicatedConnection = dedicatedClient.connect();
            }
            return "PONG".equals(dedicatedConnection.sync().ping());
        } catch (Exception e) {
            return false;
        }
    }
}
