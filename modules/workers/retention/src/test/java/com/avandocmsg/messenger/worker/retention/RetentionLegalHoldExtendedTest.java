package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionLegalHoldExtendedTest {

    @Test
    void fileRetentionSql_skipsWhenOrgLegalHoldFiles() {
        assertTrue(FileRetentionJanitor.candidateSelectSql().contains("legal_hold_files = true"));
    }
}
