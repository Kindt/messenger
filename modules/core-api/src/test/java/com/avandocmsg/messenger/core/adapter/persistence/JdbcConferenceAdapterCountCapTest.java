package com.avandocmsg.messenger.core.adapter.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcConferenceAdapterCountCapTest {

    @Test
    void countActiveParticipants_hasUpperBoundConstant() {
        assertEquals(10_000, JdbcConferenceAdapter.COUNT_ACTIVE_PARTICIPANTS_LIMIT);
    }
}
