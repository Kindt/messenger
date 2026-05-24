package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plan 02 §3.1 — unused file_metadata candidate SQL. */
class FileRepositoryUnusedTest {

    @Test
    void candidateSql_excludesReferencedFiles() {
        var sql = FileRetentionJanitor.candidateSelectSql();
        assertTrue(sql.contains("attachment_file_id"));
        assertTrue(sql.contains("file://"));
    }
}
