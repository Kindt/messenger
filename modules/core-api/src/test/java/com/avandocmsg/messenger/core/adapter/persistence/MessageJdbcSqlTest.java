package com.avandocmsg.messenger.core.adapter.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageJdbcSqlTest {

    @Test
    void visibilityClause_usesIntervalNotExtract() {
        var clause = MessageJdbcSql.MSG_VISIBILITY_TTL_VISIBLE;
        assertTrue(clause.contains("INTERVAL"));
        assertFalse(clause.contains("EXTRACT"));
    }
}
