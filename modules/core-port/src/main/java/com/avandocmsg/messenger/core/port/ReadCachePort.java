package com.avandocmsg.messenger.core.port;

import java.util.Optional;

/**
 * Cache-aside port for hot read paths (spec 006 FR-OPT-03).
 * Values are opaque UTF-8 JSON; callers own serialization.
 */
public interface ReadCachePort {

    boolean enabled();

    Optional<String> get(String key);

    void put(String key, String jsonValue, int ttlSeconds);

    void invalidate(String key);
}
