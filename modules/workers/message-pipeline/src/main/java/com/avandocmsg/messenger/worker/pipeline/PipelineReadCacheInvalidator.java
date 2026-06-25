package com.avandocmsg.messenger.worker.pipeline;

import com.avandocmsg.messenger.common.cache.ReadCacheRedisKeys;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.UUID;

/**
 * Direct Redis DEL for read-cache keys after message fan-out (spec 025 FR-009).
 * Replaces NATS {@code msg.cache.invalidate} publish from pipeline.
 */
public final class PipelineReadCacheInvalidator implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(PipelineReadCacheInvalidator.class);

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> redis;
    private final boolean enabled;

    private PipelineReadCacheInvalidator(RedisClient client, StatefulRedisConnection<String, String> connection,
                                         RedisCommands<String, String> redis, boolean enabled) {
        this.client = client;
        this.connection = connection;
        this.redis = redis;
        this.enabled = enabled;
    }

    public static PipelineReadCacheInvalidator fromEnv() {
        var cacheEnabled = !"false".equalsIgnoreCase(System.getenv().getOrDefault("REDIS_READ_CACHE_ENABLED", "true"));
        var uri = System.getenv("REDIS_URI");
        if (!cacheEnabled || uri == null || uri.isBlank()) {
            return disabled();
        }
        try {
            var client = RedisClient.create(RedisURI.create(uri.trim()));
            var connection = client.connect();
            log.info("Pipeline read-cache invalidator connected to Redis");
            return new PipelineReadCacheInvalidator(client, connection, connection.sync(), true);
        } catch (Exception e) {
            log.warn("Pipeline read-cache invalidator disabled: {}", e.getMessage());
            return disabled();
        }
    }

    static PipelineReadCacheInvalidator disabled() {
        return new PipelineReadCacheInvalidator(null, null, null, false);
    }

    static PipelineReadCacheInvalidator forTest(RedisCommands<String, String> redis) {
        return new PipelineReadCacheInvalidator(null, null, redis, redis != null);
    }

    public void invalidateAfterMessageSend(Collection<String> userIds, UUID senderId) {
        if (!enabled || redis == null || userIds == null) {
            return;
        }
        try {
            if (senderId != null) {
                invalidateUser(senderId.toString());
            }
            for (var raw : userIds) {
                if (raw != null && !raw.isBlank()) {
                    invalidateUser(raw);
                }
            }
        } catch (Exception e) {
            log.debug("read-cache DEL failed: {}", e.getMessage());
        }
    }

    private void invalidateUser(String userId) {
        redis.del(ReadCacheRedisKeys.chatList(userId), ReadCacheRedisKeys.chatUnread(userId));
    }

    public boolean enabled() {
        return enabled;
    }

    @Override
    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception e) {
            log.debug("Redis connection close: {}", e.getMessage());
        }
        try {
            if (client != null) {
                client.shutdown();
            }
        } catch (Exception e) {
            log.debug("Redis client shutdown: {}", e.getMessage());
        }
    }
}
