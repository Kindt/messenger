package com.avandocmsg.messenger.core.adapter.cache;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.metrics.ReadCacheMetrics;
import com.avandocmsg.messenger.core.port.ReadCacheKind;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/** Lettuce-backed cache-aside adapter; fail-open on Redis errors. */
public final class RedisReadCacheAdapter implements ReadCachePort {
    private static final Logger log = LoggerFactory.getLogger(RedisReadCacheAdapter.class);

    private final RedisCommands<String, String> redis;
    private final AppConfig appConfig;

    public RedisReadCacheAdapter(RedisCommands<String, String> redis, AppConfig appConfig) {
        this.redis = redis;
        this.appConfig = appConfig;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Optional<String> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            var value = redis.get(key);
            if (value == null || value.isEmpty()) {
                ReadCacheMetrics.miss(cacheKind(key));
                return Optional.empty();
            }
            ReadCacheMetrics.hit(cacheKind(key));
            return Optional.of(value);
        } catch (Exception e) {
            log.warn("Read cache get failed for key {}: {}", key, e.toString());
            ReadCacheMetrics.miss(cacheKind(key));
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, String jsonValue, int ttlSeconds) {
        if (key == null || key.isBlank() || jsonValue == null) {
            return;
        }
        var ttl = ttlSeconds > 0 ? ttlSeconds : appConfig.readCacheTtlSeconds(ReadCacheKind.CHAT_LIST);
        try {
            redis.set(key, jsonValue);
            redis.expire(key, ttl);
        } catch (Exception e) {
            log.warn("Read cache put failed for key {}: {}", key, e.toString());
        }
    }

    @Override
    public void invalidate(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            redis.del(key);
        } catch (Exception e) {
            log.warn("Read cache invalidate failed for key {}: {}", key, e.toString());
        }
    }

    private static String cacheKind(String key) {
        if (key == null) {
            return "unknown";
        }
        if (key.contains(":chat:list:")) {
            return "chat_list";
        }
        if (key.contains(":chat:unread:")) {
            return "chat_unread";
        }
        if (key.contains(":user:profile:")) {
            return "user_profile";
        }
        if (key.contains(":user:presence:")) {
            return "user_presence";
        }
        return "unknown";
    }
}
