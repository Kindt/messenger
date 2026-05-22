package com.avandocmsg.messenger.worker.exportreplay;



import org.slf4j.Logger;

import org.slf4j.LoggerFactory;



import javax.sql.DataSource;

import java.util.UUID;



/** Updates {@code export_jobs} rows created by core-api {@code ExportResource}. */

final class ExportJobStore {

    private static final Logger log = LoggerFactory.getLogger(ExportJobStore.class);



    private final DataSource dataSource;



    ExportJobStore(DataSource dataSource) {

        this.dataSource = dataSource;

    }



    void markProcessing(UUID jobId) {
        markProcessingIfQueued(jobId);
    }

    /**
     * @return false if job was cancelled or already taken (not {@code queued})
     */
    boolean markProcessingIfQueued(UUID jobId) {
        var sql = """
            UPDATE export_jobs SET status = 'processing', updated_at = now()
            WHERE id = ? AND status = 'queued'
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, jobId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("export_jobs markProcessingIfQueued failed jobId={}: {}", jobId, e.getMessage());
            return false;
        }
    }



    void markTerminal(UUID jobId, String status, String outputPath, boolean messageTtlFilterApplied) {
        update(jobId, status, outputPath, messageTtlFilterApplied, true);
    }

    boolean isCancelled(UUID jobId) {
        return loadStatus(jobId).map(s -> STATUS_CANCELLED.equals(s)).orElse(false);
    }

    java.util.Optional<String> loadStatus(UUID jobId) {
        var sql = "SELECT status FROM export_jobs WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, jobId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return java.util.Optional.ofNullable(rs.getString("status"));
                }
            }
        } catch (Exception e) {
            log.warn("export_jobs loadStatus failed jobId={}: {}", jobId, e.getMessage());
        }
        return java.util.Optional.empty();
    }

    static final String STATUS_CANCELLED = "export_cancelled";

    private void update(UUID jobId, String status, String outputPath, Boolean messageTtlFilterApplied, boolean terminal) {

        var sql = terminal

            ? """

            UPDATE export_jobs SET status = ?, output_path = ?, message_ttl_filter_applied = ?,

                updated_at = now(), completed_at = now()

            WHERE id = ? AND status = 'processing'

            """

            : """

            UPDATE export_jobs SET status = ?, updated_at = now()

            WHERE id = ?

            """;

        try (var conn = dataSource.getConnection();

             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);

            if (terminal) {

                stmt.setString(2, outputPath);

                stmt.setBoolean(3, messageTtlFilterApplied);

                stmt.setObject(4, jobId);

            } else {

                stmt.setObject(2, jobId);

            }

            var n = stmt.executeUpdate();

            if (n == 0) {

                log.warn("export_jobs row not found for jobId={}", jobId);

            }

        } catch (Exception e) {

            log.warn("export_jobs update failed jobId={} status={}: {}", jobId, status, e.getMessage());

        }

    }

}


