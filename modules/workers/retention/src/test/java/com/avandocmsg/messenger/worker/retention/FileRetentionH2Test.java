package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Query-side tests for {@link FileRetentionJanitor} (H2 integration deferred — PostgreSQL-only SQL). */
class FileRetentionH2Test {

    @Test
    void candidateSql_excludesReferencedFilesAndLegalHold() {
        var sql = FileRetentionJanitor.candidateSelectSql();
        assertTrue(sql.contains("legal_hold_files"));
        assertTrue(sql.contains("attachment_file_id"));
        assertTrue(sql.contains("file_public_links"));
    }
}
