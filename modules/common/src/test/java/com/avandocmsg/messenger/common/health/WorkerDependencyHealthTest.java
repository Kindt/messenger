package com.avandocmsg.messenger.common.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkerDependencyHealthTest {

    @Test
    void natsConnected_falseWhenNull() {
        assertFalse(WorkerDependencyHealth.natsConnected(null));
    }

    @Test
    void jdbcReachable_falseWhenNull() {
        assertFalse(WorkerDependencyHealth.jdbcReachable(null));
    }

    @Test
    void natsAndOptionalJdbc_falseWhenNatsNull() {
        assertFalse(WorkerDependencyHealth.natsAndOptionalJdbc(null, null));
    }

    @Test
    void natsAndJdbc_falseWhenEitherNull() {
        assertFalse(WorkerDependencyHealth.natsAndJdbc(null, null));
    }
}
