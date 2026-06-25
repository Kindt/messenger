package com.avandocmsg.messenger.core.adapter.persistence;

/** Upper bounds for JDBC list scans and admin/metrics counts (spec 025 FR-007, FR-128). */
public final class JdbcListLimits {
    /** Max rows scanned for unbounded admin {@code COUNT(*)} on large tables (returns min(actual, cap)). */
    public static final int COUNT_CAP_ADMIN = 10_000;
    public static final int MESSAGE_VERSIONS = 100;
    public static final int MESSAGE_REACTIONS = 500;
    public static final int PINNED_MESSAGES = 100;
    public static final int CHAT_BANS = 1_000;
    public static final int ORGANIZATIONS = 1_000;
    public static final int CHAT_MEMBERS = 10_000;
    public static final int USER_CHATS = 5_000;
    public static final int CONTACTS = 5_000;
    public static final int PHONE_HASH_MATCHES = 100;
    public static final int DEVICES = 100;
    public static final int BLOCKED_USERS = 5_000;
    public static final int FEDERATION_TRUST = 1_000;

    private JdbcListLimits() {
    }
}
