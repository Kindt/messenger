package com.avandocmsg.messenger.core.adapter.cache;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.bootstrap.CoreModule;
import com.avandocmsg.messenger.core.port.ReadCacheKeys;
import com.avandocmsg.messenger.core.port.ReadCacheKind;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisReadCacheAdapterTest {

    @Test
    void noopWhenDisabled() {
        var port = CoreModule.readCachePort(null, cfg(false));
        assertFalse(port.enabled());
        assertTrue(port.get("any").isEmpty());
    }

    @Test
    void getPutInvalidate_roundTrip() {
        var store = new HashMap<String, String>();
        var redis = mockRedis(store);
        var port = new RedisReadCacheAdapter(redis, cfg(true));
        var userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var key = ReadCacheKeys.chatList(userId);

        assertTrue(port.get(key).isEmpty());
        port.put(key, "{\"chats\":[]}", ReadCacheKind.CHAT_LIST.defaultTtlSeconds());
        assertEquals("{\"chats\":[]}", port.get(key).orElseThrow());

        port.invalidate(key);
        assertTrue(port.get(key).isEmpty());
        verify(redis).del(key);
    }

    @Test
    void get_failOpenOnRedisError() {
        @SuppressWarnings("unchecked")
        RedisCommands<String, String> redis = mock(RedisCommands.class);
        when(redis.get(anyString())).thenThrow(new RuntimeException("redis down"));
        var port = new RedisReadCacheAdapter(redis, cfg(true));
        assertTrue(port.get(ReadCacheKeys.userProfile(UUID.randomUUID())).isEmpty());
    }

    @Test
    void put_usesDefaultTtlWhenNonPositive() {
        var store = new HashMap<String, String>();
        var redis = mockRedis(store);
        var port = new RedisReadCacheAdapter(redis, cfg(true));
        var key = ReadCacheKeys.chatUnread(UUID.randomUUID());
        port.put(key, "{}", 0);
        verify(redis).expire(key, 60L);
    }

    @SuppressWarnings("unchecked")
    private static RedisCommands<String, String> mockRedis(Map<String, String> store) {
        RedisCommands<String, String> redis = mock(RedisCommands.class);
        when(redis.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return "OK";
        }).when(redis).set(anyString(), anyString());
        when(redis.expire(anyString(), anyLong())).thenReturn(true);
        when(redis.del(ArgumentMatchers.<String>any())).thenAnswer(inv -> {
            store.remove(inv.getArgument(0));
            return 1L;
        });
        return redis;
    }

    private static AppConfig cfg(boolean readCacheEnabled) {
        return new AppConfig() {
            @Override
            public boolean redisReadCacheEnabled() {
                return readCacheEnabled;
            }

            @Override
            public int readCacheTtlSeconds(ReadCacheKind kind) {
                return kind.defaultTtlSeconds();
            }
        };
    }
}
