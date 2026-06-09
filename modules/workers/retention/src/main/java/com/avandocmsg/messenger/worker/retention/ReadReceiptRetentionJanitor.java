package com.avandocmsg.messenger.worker.retention;

import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/** Purges old rows from {@code message_read_receipts} when {@code READ_RECEIPT_RETENTION_DAYS} &gt; 0. */
final class ReadReceiptRetentionJanitor {
    private static final Logger log = LoggerFactory.getLogger(ReadReceiptRetentionJanitor.class);

    private ReadReceiptRetentionJanitor() {
    }

    static int purgeOldReceipts(DataSource dataSource, int retentionDays, UserMessageSource workerMessages) {
        if (dataSource == null || retentionDays <= 0) {
            return 0;
        }
        var sql = """
            DELETE FROM message_read_receipts
            WHERE read_at < now() - (? * INTERVAL '1 day')
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, retentionDays);
            var deleted = stmt.executeUpdate();
            if (deleted > 0) {
                log.info(workerMessages.format("worker.retention.read_receipt.purged", deleted, retentionDays));
            }
            return deleted;
        } catch (Exception e) {
            log.warn(workerMessages.format("worker.retention.read_receipt.failed", e.getMessage()));
            return 0;
        }
    }
}
