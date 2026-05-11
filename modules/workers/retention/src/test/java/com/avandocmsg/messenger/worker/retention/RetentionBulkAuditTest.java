package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionBulkAuditTest {

    @Test
    void shouldRecordSummary_thresholdZero_disabled() {
        assertFalse(RetentionBulkAudit.shouldRecordSummary(100, 0));
        assertFalse(RetentionBulkAudit.shouldRecordSummary(1, 0));
    }

    @Test
    void shouldRecordSummary_metWhenClearedEqualsOrExceedsThreshold() {
        assertTrue(RetentionBulkAudit.shouldRecordSummary(10, 10));
        assertTrue(RetentionBulkAudit.shouldRecordSummary(11, 10));
        assertFalse(RetentionBulkAudit.shouldRecordSummary(9, 10));
    }
}
