package com.avandocmsg.messenger.api.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
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
        uri.setTimeout(appConfig.redisCommandTimeout());
        this.client = RedisClient.create(uri);
        client.setOptions(ClientOptions.builder()
            .autoReconnect(true)
            .socketOptions(SocketOptions.builder()
                .connectTimeout(appConfig.redisConnectTimeout())
                .keepAlive(true)
                .build())
            .timeoutOptions(TimeoutOptions.builder()
                .fixedTimeout(appConfig.redisCommandTimeout())
                .build())
            .build());
        this.connection = client.connect();
        log.info(
            "Redis connected: {} (connectTimeout={}ms, commandTimeout={}ms)",
            appConfig.redisUri(),
            appConfig.redisConnectTimeout().toMillis(),
            appConfig.redisCommandTimeout().toMillis());
    }

    public RedisCommands<String, String> sync() {
        return connection.sync();
    }

    public void shutdown() {
        connection.close();
        client.shutdown();
    }
}
