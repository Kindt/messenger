package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcExportJobJdbcRepository;

import javax.sql.DataSource;

/** DB counts for export_jobs stuck in {@code processing}. */
public final class ExportJobStaleCounts {

    private ExportJobStaleCounts() {
    }

    public static long countProcessingStale(DataSource dataSource, int staleMinutes) throws Exception {
        return new JdbcExportJobJdbcRepository(dataSource).countProcessingStale(staleMinutes);
    }
}
