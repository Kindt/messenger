package com.avandocmsg.messenger.api.export;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** DB counts for export_jobs stuck in {@code processing}. */
public final class ExportJobStaleCounts {

    private ExportJobStaleCounts() {
    }

    public static long countProcessingStale(DataSource dataSource, int staleMinutes) throws Exception {
        if (staleMinutes < 1) {
            staleMinutes = 1;
        }
        var cutoff = Timestamp.from(Instant.now().minus(staleMinutes, ChronoUnit.MINUTES));
        try (var conn = dataSource.getConnection();
             var st = conn.prepareStatement(
                 """
                 SELECT COUNT(*) FROM export_jobs
                 WHERE status = 'processing' AND updated_at < ?
                 """)) {
            st.setTimestamp(1, cutoff);
            try (var rs = st.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }
}
