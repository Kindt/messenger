package com.avandocmsg.messenger.core.adapter.persistence;

/** Shared SQL fragments for message visibility (hex JDBC layer). */
public final class MessageJdbcSql {
    /** Message row visible by {@code visibility_ttl_seconds} relative to {@code created_at}. */
    public static final String MSG_VISIBILITY_TTL_VISIBLE =
        "(m.visibility_ttl_seconds IS NULL OR EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - m.created_at)) < m.visibility_ttl_seconds)";

    private MessageJdbcSql() {
    }
}
