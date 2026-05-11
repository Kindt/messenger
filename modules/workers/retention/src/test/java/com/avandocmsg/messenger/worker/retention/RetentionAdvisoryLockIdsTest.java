package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionAdvisoryLockIdsTest {

    @Test
    void tryLockQuery_embedsBothKeyLiterals() {
        var q = RetentionAdvisoryLockIds.tryLockQuery();
        assertTrue(q.startsWith("SELECT pg_try_advisory_lock("));
        assertTrue(q.contains(String.valueOf(RetentionAdvisoryLockIds.SESSION_KEY_1)));
        assertTrue(q.contains(String.valueOf(RetentionAdvisoryLockIds.SESSION_KEY_2)));
        assertTrue(q.endsWith(")"));
    }

    @Test
    void unlockQuery_embedsBothKeyLiterals() {
        var q = RetentionAdvisoryLockIds.unlockQuery();
        assertTrue(q.startsWith("SELECT pg_advisory_unlock("));
        assertTrue(q.contains(String.valueOf(RetentionAdvisoryLockIds.SESSION_KEY_1)));
        assertTrue(q.contains(String.valueOf(RetentionAdvisoryLockIds.SESSION_KEY_2)));
    }

    @Test
    void keysMatchJavaUuidBitHalves() {
        var u = java.util.UUID.fromString("6b0f8e2c-8d1a-4f3e-9c7b-0a1b2c3d4e5f");
        assertEquals((int) (u.getMostSignificantBits() >>> 32), RetentionAdvisoryLockIds.SESSION_KEY_1);
        assertEquals((int) u.getLeastSignificantBits(), RetentionAdvisoryLockIds.SESSION_KEY_2);
    }
}
