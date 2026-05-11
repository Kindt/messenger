package com.avandocmsg.messenger.worker.retention;

/**
 * Summary audit row for a hot-body pass when many messages were cleared in one run (see {@code RETENTION_BULK_AUDIT_MIN_CLEARED}).
 */
final class RetentionBulkAudit {
    private RetentionBulkAudit() {
    }

    /**
     * @param minClearedThreshold {@code RETENTION_BULK_AUDIT_MIN_CLEARED}; {@code 0} means feature off
     */
    static boolean shouldRecordSummary(int clearedCount, int minClearedThreshold) {
        return minClearedThreshold > 0 && clearedCount >= minClearedThreshold;
    }
}
