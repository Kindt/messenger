package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionPurgeH2Test {

    @Test
    void purgeCandidateSql_matchesHotRowPurgerContract() {
        var sql = RetentionHotRowPurger.purgeCandidateSelectSql();
        assertTrue(sql.contains("DELETE FROM messages") || sql.contains("retention_hot_body_applied"));
        assertTrue(RetentionHotRowPurger.purgeCandidateSelectSql().contains("m.content IS NULL"));
    }
}
