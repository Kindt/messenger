package com.avandocmsg.messenger.core.adapter.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcListLimitsTest {

    @Test
    void limits_arePositive() {
        assertTrue(JdbcListLimits.MESSAGE_VERSIONS > 0);
        assertTrue(JdbcListLimits.CHAT_MEMBERS > 0);
        assertTrue(JdbcListLimits.FEDERATION_TRUST > 0);
        assertTrue(JdbcListLimits.COUNT_CAP_ADMIN > 0);
    }
}
