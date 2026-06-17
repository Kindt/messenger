package com.avandocmsg.messenger.api.auth;

import com.avandocmsg.messenger.api.config.AppConfig;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimiterTest {

    @Test
    void noop_alwaysAllows() {
        var lim = AuthRateLimiter.noop();
        assertTrue(lim.allowLogin("1.2.3.4"));
        assertTrue(lim.allowRegister("1.2.3.4"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void allowLogin_blocksWhenOverLimit() {
        var cfg = mockConfig(2, 5);
        AtomicLong evalResult = new AtomicLong(3L);
        RedisCommands<String, String> redis = evalOnlyRedis(() -> evalResult.get());
        var lim = AuthRateLimiter.redis(redis, cfg);
        assertFalse(lim.allowLogin("10.0.0.1"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void allowLogin_allowsAtBoundary() {
        var cfg = mockConfig(3, 5);
        AtomicLong evalResult = new AtomicLong(3L);
        RedisCommands<String, String> redis = evalOnlyRedis(() -> evalResult.get());
        var lim = AuthRateLimiter.redis(redis, cfg);
        assertTrue(lim.allowLogin("10.0.0.2"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void redisFailure_failOpenWhenConfigured() {
        var cfg = mockConfig(1, 1, true);
        RedisCommands<String, String> redis = evalOnlyRedis(() -> {
            throw new RuntimeException("redis down");
        });
        var lim = AuthRateLimiter.redis(redis, cfg);
        assertTrue(lim.allowLogin("10.0.0.3"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void redisFailure_failClosedByDefault() {
        var cfg = mockConfig(1, 1, false);
        RedisCommands<String, String> redis = evalOnlyRedis(() -> {
            throw new RuntimeException("redis down");
        });
        var lim = AuthRateLimiter.redis(redis, cfg);
        assertFalse(lim.allowLogin("10.0.0.4"));
    }

    @Test
    void clientIp_usesXForwardedForFirstHop() {
        var req = request("203.0.113.5, 10.0.0.1", null);
        assertEquals("203.0.113.5", AuthRateLimiter.clientIp(req));
    }

    @Test
    void clientIp_fallsBackToRemoteAddr() {
        var req = request(null, "192.0.2.1");
        assertEquals("192.0.2.1", AuthRateLimiter.clientIp(req));
    }

    @Test
    void clientIp_nullRequest() {
        assertEquals("unknown", AuthRateLimiter.clientIp(null));
    }

    private static AppConfig mockConfig(int loginPerMin, int regPerHour) {
        return mockConfig(loginPerMin, regPerHour, false);
    }

    private static AppConfig mockConfig(int loginPerMin, int regPerHour, boolean failOpen) {
        return new AppConfig() {
            @Override
            public int rateLimitLoginMaxPerMinute() {
                return loginPerMin;
            }

            @Override
            public int rateLimitRegisterMaxPerHour() {
                return regPerHour;
            }

            @Override
            public boolean rateLimitAuthFailOpen() {
                return failOpen;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static RedisCommands<String, String> evalOnlyRedis(EvalAction action) {
        return (RedisCommands<String, String>) Proxy.newProxyInstance(
            AuthRateLimiterTest.class.getClassLoader(),
            new Class<?>[]{RedisCommands.class},
            (proxy, method, args) -> {
                if ("eval".equals(method.getName()) && args != null && args.length >= 4) {
                    return action.run();
                }
                if (method.getDeclaringClass() == Object.class) {
                    return method.invoke(proxy, args);
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }

    @FunctionalInterface
    private interface EvalAction {
        Object run() throws Exception;
    }

    private static HttpServletRequest request(String xff, String remote) {
        return (HttpServletRequest) Proxy.newProxyInstance(
            AuthRateLimiterTest.class.getClassLoader(),
            new Class<?>[]{HttpServletRequest.class},
            (proxy, method, args) -> {
                if ("getHeader".equals(method.getName()) && args != null && args.length == 1) {
                    return "X-Forwarded-For".equals(args[0]) ? xff : null;
                }
                if ("getRemoteAddr".equals(method.getName())) {
                    return remote;
                }
                if (method.getDeclaringClass() == Object.class) {
                    return method.invoke(proxy, args);
                }
                return null;
            });
    }
}
