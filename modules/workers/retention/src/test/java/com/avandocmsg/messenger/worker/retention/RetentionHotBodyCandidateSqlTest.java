package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard: candidate selection must merge platform/org/chat policy and require
 * {@code eff_legal = false} and {@code eff_deep = true} so legal hold cannot be dropped from SQL
 * without failing tests (no PostgreSQL required). Soft-deleted rows ({@code messages.deleted})
 * must not be candidates.
 */
class RetentionHotBodyCandidateSqlTest {

    @Test
    void candidateSql_requiresEffectiveLegalFalseAndDeepTrue() {
        for (boolean useAppliedLog : new boolean[] { true, false }) {
            String sql = RetentionHotBodyJanitor.hotBodyCandidateSelectSql(useAppliedLog);
            assertTrue(sql.contains(" AS eff_legal"), "useAppliedLog=" + useAppliedLog);
            assertTrue(sql.contains(" AS eff_deep"), "useAppliedLog=" + useAppliedLog);
            assertTrue(sql.contains(" AS eff_body_days"), "useAppliedLog=" + useAppliedLog);
            assertTrue(sql.contains("pol.eff_legal = false"), "useAppliedLog=" + useAppliedLog);
            assertTrue(sql.contains("pol.eff_deep = true"), "useAppliedLog=" + useAppliedLog);
            assertTrue(sql.contains("crp.legal_hold"), "useAppliedLog=" + useAppliedLog);
            assertTrue(sql.contains("deep_archive_enabled"), "useAppliedLog=" + useAppliedLog);
            assertTrue(sql.contains("m.deleted = false"), "useAppliedLog=" + useAppliedLog);
        }
    }

    @Test
    void candidateSql_withAppliedLog_excludesAlreadyApplied() {
        String sql = RetentionHotBodyJanitor.hotBodyCandidateSelectSql(true);
        assertTrue(sql.contains("retention_hot_body_applied"));
        assertTrue(sql.contains("NOT EXISTS"));
    }

    @Test
    void candidateSql_withoutAppliedLog_omitsAppliedFilter() {
        String sql = RetentionHotBodyJanitor.hotBodyCandidateSelectSql(false);
        assertFalse(sql.contains("retention_hot_body_applied"));
    }
}
