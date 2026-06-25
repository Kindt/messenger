package com.avandocmsg.messenger.core.adapter.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcMessageReadRepositorySearchClauseTest {

    @Test
    void plaintextSearchClause_usesTsvectorOnPostgres() {
        var clause = JdbcMessageReadRepository.plaintextSearchClause(true);
        assertTrue(clause.contains("to_tsvector"));
        assertTrue(clause.contains("plainto_tsquery"));
    }

    @Test
    void plaintextSearchClause_fallsBackToPositionOnH2() {
        var clause = JdbcMessageReadRepository.plaintextSearchClause(false);
        assertTrue(clause.contains("POSITION"));
    }
}
