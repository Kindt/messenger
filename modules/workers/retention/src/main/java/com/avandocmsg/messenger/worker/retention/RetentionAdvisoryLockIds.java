package com.avandocmsg.messenger.worker.retention;

/**
 * PostgreSQL session advisory lock identity for {@link RetentionHotBodyJanitor} hot-body passes.
 * <p>
 * Used with {@code pg_try_advisory_lock(int, int)} / {@code pg_advisory_unlock(int, int)} (session scope,
 * one database). When {@code RETENTION_USE_ADVISORY_LOCK=true} and {@code DB_JDBC_URL} is {@code jdbc:postgresql:…},
 * a single JDBC connection holds this lock for the entire pass so only one replica runs the expensive
 * candidate scan and per-message Hot DB mutations at a time on a shared Hot DB.
 * <p>
 * <b>Key derivation:</b> fixed UUID {@code 6b0f8e2c-8d1a-4f3e-9c7b-0a1b2c3d4e5f} (“namespace” for Korus Messenger
 * retention hot-body coordinator). The two {@code int} keys are the high and low 32 bits of the UUID’s
 * 128-bit value (RFC 4122 layout as produced by {@link java.util.UUID#getMostSignificantBits()} /
 * {@link java.util.UUID#getLeastSignificantBits()}), i.e. {@code uuid.getMostSignificantBits() >> 32} and
 * {@code (int) uuid.getLeastSignificantBits()} — chosen so the pair is stable, documented, and unlikely to
 * collide with ad-hoc application locks that use unrelated key pairs.
 */
public final class RetentionAdvisoryLockIds {
    /** High 32 bits of namespace UUID {@code 6b0f8e2c-8d1a-4f3e-9c7b-0a1b2c3d4e5f} (signed Java {@code int}). */
    public static final int SESSION_KEY_1 = (int) (0x6b0f8e2c8d1a4f3eL >>> 32);
    /** Low 32 bits of the same UUID’s least-significant word (per Java {@link java.util.UUID} bit layout). */
    public static final int SESSION_KEY_2 = (int) 0x9c7b0a1b2c3d4e5fL;

    private RetentionAdvisoryLockIds() {
    }

    /**
     * Non-blocking session try-lock; returns one row with a single {@code boolean} column.
     * Literals are fixed so the statement needs no bind parameters.
     */
    public static String tryLockQuery() {
        return "SELECT pg_try_advisory_lock(" + SESSION_KEY_1 + ", " + SESSION_KEY_2 + ")";
    }

    /** Session unlock matching {@link #tryLockQuery()}; one row, single {@code boolean}. */
    public static String unlockQuery() {
        return "SELECT pg_advisory_unlock(" + SESSION_KEY_1 + ", " + SESSION_KEY_2 + ")";
    }
}
