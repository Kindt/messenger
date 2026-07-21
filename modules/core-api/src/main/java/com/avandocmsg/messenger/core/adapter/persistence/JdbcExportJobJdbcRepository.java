package com.avandocmsg.messenger.core.adapter.persistence;


import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;


import org.slf4j.Logger;

import org.slf4j.LoggerFactory;



import javax.sql.DataSource;

import java.sql.Timestamp;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import java.util.UUID;



public final class JdbcExportJobJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcExportJobJdbcRepository.class);

    private final DataSource dataSource;



    public JdbcExportJobJdbcRepository(DataSource dataSource) {

        this.dataSource = dataSource;

    }



    public void insertQueued(UUID jobId, UUID chatId, UUID requestedBy) {

        var sql = """

            INSERT INTO export_jobs (id, chat_id, requested_by, status, created_at, updated_at)

            VALUES (?, ?, ?, 'queued', now(), now())

            """;

        try (var conn = dataSource.getConnection();

             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);

            stmt.setObject(1, jobId);

            stmt.setObject(2, chatId);

            stmt.setObject(3, requestedBy);

            stmt.executeUpdate();

        } catch (Exception e) {

            log.error("Failed to insert export job {}", jobId, e);

            throw new IllegalStateException("export job insert failed", e);

                }

    }



    public Optional<ExportJobRow> findLatestForChat(UUID chatId) {
        var sql = """
            SELECT id, chat_id, requested_by, status, output_path, message_ttl_filter_applied,
                   created_at, updated_at, completed_at
            FROM export_jobs WHERE chat_id = ?
            ORDER BY created_at DESC
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.warn("find latest export job chat {}: {}", chatId, e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return Optional.empty();
    }

    /** Latest terminal export job for a chat ({@code export_v1} only). */
    public Optional<ExportJobRow> findLatestCompletedExport(UUID chatId) {
        var sql = """
            SELECT id, chat_id, requested_by, status, output_path, message_ttl_filter_applied,
                   created_at, updated_at, completed_at
            FROM export_jobs WHERE chat_id = ? AND status = 'export_v1'
            ORDER BY completed_at DESC NULLS LAST, created_at DESC
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.warn("find latest completed export chat {}: {}", chatId, e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return Optional.empty();
    }

    public boolean isExportSufficientForPurge(UUID chatId) {
        return findLatestCompletedExport(chatId).isPresent();
    }

    /**
     * Blocks a new auto-queue when a job is pending or a non-failed job was created within {@code cooldownMinutes}.
     * {@code cooldownMinutes <= 0} — only {@code queued}/{@code processing} block.
     */
    public boolean hasBlockingJobForChat(UUID chatId, int cooldownMinutes) {
        var latest = findLatestForChat(chatId);
        if (latest.isEmpty()) {
            return false;
        }
        var row = latest.get();
        if ("queued".equals(row.status()) || "processing".equals(row.status())) {
            return true;
        }
        if ("export_failed".equals(row.status()) || "export_cancelled".equals(row.status()) || cooldownMinutes <= 0) {
            return false;
        }
        var cutoff = java.time.Instant.now().minusSeconds(cooldownMinutes * 60L);
        return row.createdAt() != null && row.createdAt().isAfter(cutoff);
    }

    public Optional<ExportJobRow> findByIdAndChat(UUID jobId, UUID chatId) {

        var sql = """

            SELECT id, chat_id, requested_by, status, output_path, message_ttl_filter_applied,

                   created_at, updated_at, completed_at

            FROM export_jobs WHERE id = ? AND chat_id = ?

            """;

        try (var conn = dataSource.getConnection();

             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);

            stmt.setObject(1, jobId);

            stmt.setObject(2, chatId);

            try (var rs = stmt.executeQuery()) {

                if (rs.next()) {

                    return Optional.of(mapRow(rs));

                }

            }

        } catch (Exception e) {

            log.error("find export job {} chat {}", jobId, chatId, e);
            throw new IllegalStateException("JDBC operation failed", e);
        }

        return Optional.empty();

    }

    public List<ExportJobRow> listForChat(UUID chatId, String statusFilter, int limit) {
        var lim = Math.min(Math.max(limit, 1), 100);
        var hasStatus = statusFilter != null && !statusFilter.isBlank();
        var sql = hasStatus
            ? """
            SELECT id, chat_id, requested_by, status, output_path, message_ttl_filter_applied,
                   created_at, updated_at, completed_at
            FROM export_jobs WHERE chat_id = ? AND status = ?
            ORDER BY created_at DESC
            LIMIT ?
            """
            : """
            SELECT id, chat_id, requested_by, status, output_path, message_ttl_filter_applied,
                   created_at, updated_at, completed_at
            FROM export_jobs WHERE chat_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        var rows = new ArrayList<ExportJobRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, chatId);
            if (hasStatus) {
                stmt.setString(2, statusFilter.trim());
                stmt.setInt(3, lim);
            } else {
                stmt.setInt(2, lim);
            }
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.warn("list export jobs chat {}: {}", chatId, e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return List.copyOf(rows);
    }

    /**
     * Recent jobs across all chats (admin). Optional {@code statusFilter} and {@code chatIdFilter}.
     */
    public List<ExportJobRow> listRecent(String statusFilter, UUID chatIdFilter, int limit) {
        var lim = Math.min(Math.max(limit, 1), 100);
        var hasStatus = statusFilter != null && !statusFilter.isBlank();
        var hasChat = chatIdFilter != null;
        final String sql;
        if (hasStatus && hasChat) {
            sql = """
            SELECT id, chat_id, requested_by, status, output_path, message_ttl_filter_applied,
                   created_at, updated_at, completed_at
            FROM export_jobs WHERE status = ? AND chat_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        } else if (hasStatus) {
            sql = """
            SELECT id, chat_id, requested_by, status, output_path, message_ttl_filter_applied,
                   created_at, updated_at, completed_at
            FROM export_jobs WHERE status = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        } else if (hasChat) {
            sql = """
            SELECT id, chat_id, requested_by, status, output_path, message_ttl_filter_applied,
                   created_at, updated_at, completed_at
            FROM export_jobs WHERE chat_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        } else {
            sql = """
            SELECT id, chat_id, requested_by, status, output_path, message_ttl_filter_applied,
                   created_at, updated_at, completed_at
            FROM export_jobs
            ORDER BY created_at DESC
            LIMIT ?
            """;
        }
        var rows = new ArrayList<ExportJobRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            var idx = 1;
            if (hasStatus) {
                stmt.setString(idx++, statusFilter.trim());
            }
            if (hasChat) {
                stmt.setObject(idx++, chatIdFilter);
            }
            stmt.setInt(idx, lim);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.warn("list recent export jobs: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return List.copyOf(rows);
    }

    /**
     * Cancels jobs in {@code queued} or {@code processing}. Returns false if already terminal.
     */
    public boolean cancelIfActive(UUID jobId, UUID chatId) {
        var sql = """
            UPDATE export_jobs SET status = 'export_cancelled', updated_at = now(), completed_at = now()
            WHERE id = ? AND chat_id = ? AND status IN ('queued', 'processing')
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, jobId);
            stmt.setObject(2, chatId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("cancel export job {} chat {}: {}", jobId, chatId, e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    /**
     * Cancels only jobs still {@code queued}. Prefer {@link #cancelIfActive} for API cancel.
     */
    public boolean cancelIfQueued(UUID jobId, UUID chatId) {
        var sql = """
            UPDATE export_jobs SET status = 'export_cancelled', updated_at = now(), completed_at = now()
            WHERE id = ? AND chat_id = ? AND status = 'queued'
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, jobId);
            stmt.setObject(2, chatId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("cancel export job {} chat {}: {}", jobId, chatId, e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    public void markProcessing(UUID jobId) {

        updateStatus(jobId, "processing", null, null, false);

    }



    public void markTerminal(UUID jobId, String status, String outputPath) {

        markTerminal(jobId, status, outputPath, null);

    }



    public void markTerminal(UUID jobId, String status, String outputPath, Boolean messageTtlFilterApplied) {

        updateStatus(jobId, status, outputPath, messageTtlFilterApplied, true);

    }



    /**

     * Idempotent sync when worker publishes {@code msg.export.replay.complete} (or DB update was missed).

     * Updates only jobs still in {@code queued} or {@code processing}.

     */

    public boolean applyCompleteIfPending(UUID jobId, String status, String outputPath) {

        return applyCompleteIfPending(jobId, status, outputPath, null);

    }



    public boolean applyCompleteIfPending(UUID jobId, String status, String outputPath, Boolean messageTtlFilterApplied) {

        if (jobId == null || status == null || !isTerminalStatus(status)) {

            return false;

        }

        var sql = """

            UPDATE export_jobs SET status = ?, output_path = ?, message_ttl_filter_applied = ?,

                updated_at = now(), completed_at = now()

            WHERE id = ? AND status IN ('queued', 'processing')

            """;

        try (var conn = dataSource.getConnection();

             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);

            stmt.setString(1, status);

            stmt.setString(2, outputPath);

            if (messageTtlFilterApplied != null) {

                stmt.setBoolean(3, messageTtlFilterApplied);

            } else {

                stmt.setObject(3, null);

            }

            stmt.setObject(4, jobId);

            return stmt.executeUpdate() > 0;

        } catch (Exception e) {

            log.warn("export job complete sync failed jobId={} status={}: {}", jobId, status, e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }

    }



    private static boolean isTerminalStatus(String status) {

        return "export_v1".equals(status) || "stub_written".equals(status) || "export_failed".equals(status)
            || "export_cancelled".equals(status);

    }



    private void updateStatus(UUID jobId, String status, String outputPath, Boolean messageTtlFilterApplied, boolean terminal) {

        var sql = terminal

            ? """

            UPDATE export_jobs SET status = ?, output_path = ?, message_ttl_filter_applied = ?,

                updated_at = now(), completed_at = now()

            WHERE id = ?

            """

            : """

            UPDATE export_jobs SET status = ?, updated_at = now()

            WHERE id = ?

            """;

        try (var conn = dataSource.getConnection();

             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);

            stmt.setString(1, status);

            if (terminal) {

                stmt.setString(2, outputPath);

                if (messageTtlFilterApplied != null) {

                    stmt.setBoolean(3, messageTtlFilterApplied);

                } else {

                    stmt.setObject(3, null);

                }

                stmt.setObject(4, jobId);

            } else {

                stmt.setObject(2, jobId);

            }

            stmt.executeUpdate();

        } catch (Exception e) {

            log.warn("export job status update failed jobId={} status={}: {}", jobId, status, e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }

    }



    private static ExportJobRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {

        var ttlApplied = rs.getObject("message_ttl_filter_applied");

        Boolean messageTtlFilterApplied = ttlApplied == null ? null : rs.getBoolean("message_ttl_filter_applied");

        return new ExportJobRow(

            rs.getObject("id", UUID.class),

            rs.getObject("chat_id", UUID.class),

            rs.getObject("requested_by", UUID.class),

            rs.getString("status"),

            rs.getString("output_path"),

            messageTtlFilterApplied,

            toInstant(rs.getTimestamp("created_at")),

            toInstant(rs.getTimestamp("updated_at")),

            toInstant(rs.getTimestamp("completed_at"))

        );

    }



    private static Instant toInstant(Timestamp ts) {

        return ts != null ? ts.toInstant() : null;

    }

    public boolean existsCompletedExport(UUID chatId) {
        var sql = """
            SELECT 1 FROM export_jobs
            WHERE chat_id = ? AND status = 'export_v1'
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("Failed to check completed export for chat {}", chatId, e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }



    public long countProcessingStale(int staleMinutes) throws Exception {

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
            JdbcQuerySupport.applyDefaultTimeout(st);
            st.setTimestamp(1, cutoff);

            try (var rs = st.executeQuery()) {

                return rs.next() ? rs.getLong(1) : 0L;

            }

        }

    }



    public record ExportJobRow(

        UUID id,

        UUID chatId,

        UUID requestedBy,

        String status,

        String outputPath,

        Boolean messageTtlFilterApplied,

        Instant createdAt,

        Instant updatedAt,

        Instant completedAt

    ) {}

}
