package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionHotRowPurgerSqlTest {

    @Test
    void purgeCandidateSql_containsAppliedLogAndNullContent() {
        var sql = RetentionHotRowPurger.purgeCandidateSelectSql();
        assertTrue(sql.contains("retention_hot_body_applied"));
        assertTrue(sql.contains("m.content IS NULL"));
        assertTrue(sql.contains("eff_legal = false"));
    }
}
