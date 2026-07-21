package com.avandocmsg.messenger.api.auth;

import com.avandocmsg.messenger.api.config.AppConfig;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fixed-window rate limiting per client IP using Redis INCR + TTL (Lua atomic).
 * Disabled mode always allows traffic (no Redis).
 */
public class AuthRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);
    private static final String UNKNOWN_IP = "unknown";

    /**
     * KEYS[1] = counter key, ARGV[1] = TTL seconds for first increment.
     * Returns new counter value.
     */
    private static final String LUA_INCR_EXPIRE = """
        local n = redis.call('INCR', KEYS[1])
        if n == 1 then
          redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
        end
        return n
        """;

    private final RedisCommands<String, String> redis;
    private final AppConfig config;
    private final boolean enabled;

    private AuthRateLimiter(RedisCommands<String, String> redis, AppConfig config, boolean enabled) {
        this.redis = redis;
        this.config = config;
        this.enabled = enabled;
    }

    public static AuthRateLimiter noop() {
        return new AuthRateLimiter(null, null, false);
    }

    /**
     * Same as {@link #noop()} but {@link #allowLogout} is always false (unit tests for {@link AuthResource#logout}).
     */
    static AuthRateLimiter testingDenyLogout() {
        return new AuthRateLimiter(null, null, false) {
            @Override
            public boolean allowLogout(String clientIp) {
                return false;
            }
        };
    }

    public static AuthRateLimiter redis(RedisCommands<String, String> redis, AppConfig config) {
        return new AuthRateLimiter(redis, config, true);
    }

    public boolean allowLogin(String clientIp) {
        if (!enabled) {
            return true;
        }
        return allow("login", clientIp, config.rateLimitLoginMaxPerMinute(), 60);
    }

    public boolean allowRegister(String clientIp) {
        if (!enabled) {
            return true;
        }
        return allow("register", clientIp, config.rateLimitRegisterMaxPerHour(), 3600);
    }

    /** Тот же лимит что и login (окно 60 с), отдельный счётчик по IP. */
    public boolean allowLogout(String clientIp) {
        if (!enabled) {
            return true;
        }
        return allow("logout", clientIp, config.rateLimitLoginMaxPerMinute(), 60);
    }

    private boolean allow(String bucket, String clientIp, int max, int windowSeconds) {
        if (!enabled || redis == null) {
            return true;
        }
        var ip = safeIp(clientIp);
        var key = "rl:auth:" + bucket + ":" + ip;
        try {
            Long n = redis.eval(LUA_INCR_EXPIRE,
                ScriptOutputType.INTEGER,
                new String[]{key},
                String.valueOf(windowSeconds));
            long count = n != null ? n : 0L;
            if (count > max) {
                log.warn("Rate limit exceeded for {} bucket={} count={}/{}", ip, bucket, count, max);
                return false;
            }
            return true;
        } catch (Exception e) {
            if (config != null && config.rateLimitAuthFailOpen()) {
                log.warn("Redis rate limit check failed, allowing request (fail-open)", e);
                return true;
            }
            log.warn("Redis rate limit check failed, denying request (fail-closed)", e);
            return false;
        }
    }

    private static String safeIp(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN_IP;
        }
        return raw.length() > 128 ? raw.substring(0, 128) : raw;
    }

    /** Extract client IP: {@code X-Forwarded-For} first hop or remote address. */
    public static String clientIp(jakarta.servlet.http.HttpServletRequest req) {
        if (req == null) {
            return UNKNOWN_IP;
        }
        var xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            var first = xff.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        var addr = req.getRemoteAddr();
        return addr != null ? addr : UNKNOWN_IP;
    }
}
