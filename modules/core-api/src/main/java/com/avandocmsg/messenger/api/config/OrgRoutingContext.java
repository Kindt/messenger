package com.avandocmsg.messenger.api.config;

import java.util.UUID;

/** Thread-local org context for FR-OPT-09 routing (set by auth filter / tenant middleware). */
public final class OrgRoutingContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private OrgRoutingContext() {
    }

    public static void set(UUID orgId) {
        CURRENT.set(orgId);
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
