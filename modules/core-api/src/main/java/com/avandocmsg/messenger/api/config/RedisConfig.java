package com.avandocmsg.messenger.api.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedisConfig {
    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;

    public RedisConfig(AppConfig appConfig) {
        var uri = RedisURI.create(appConfig.redisUri());
        this.client = RedisClient.create(uri);
        this.connection = client.connect();
        log.info("Redis connected: {}", appConfig.redisUri());
    }

    public RedisCommands<String, String> sync() {
        return connection.sync();
    }

    public void shutdown() {
        connection.close();
        client.shutdown();
    }
}
