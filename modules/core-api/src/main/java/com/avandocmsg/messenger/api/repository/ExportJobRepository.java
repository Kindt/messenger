package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcExportJobJdbcRepository;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for export job JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcExportJobJdbcRepository}.
 */
public class ExportJobRepository {
    private final JdbcExportJobJdbcRepository jdbc;

    public ExportJobRepository(DataSource dataSource) {
        this.jdbc = new JdbcExportJobJdbcRepository(dataSource);
    }

    public JdbcExportJobJdbcRepository jdbcRepository() {
        return jdbc;
    }

    public void insertQueued(UUID jobId, UUID chatId, UUID requestedBy) {
        jdbc.insertQueued(jobId, chatId, requestedBy);
    }

    public Optional<ExportJobRow> findLatestForChat(UUID chatId) {
        return jdbc.findLatestForChat(chatId).map(ExportJobRepository::map);
    }

    public Optional<ExportJobRow> findLatestCompletedExport(UUID chatId) {
        return jdbc.findLatestCompletedExport(chatId).map(ExportJobRepository::map);
    }

    public boolean isExportSufficientForPurge(UUID chatId) {
        return jdbc.isExportSufficientForPurge(chatId);
    }

    public boolean hasBlockingJobForChat(UUID chatId, int cooldownMinutes) {
        return jdbc.hasBlockingJobForChat(chatId, cooldownMinutes);
    }

    public Optional<ExportJobRow> findByIdAndChat(UUID jobId, UUID chatId) {
        return jdbc.findByIdAndChat(jobId, chatId).map(ExportJobRepository::map);
    }

    public List<ExportJobRow> listForChat(UUID chatId, String statusFilter, int limit) {
        return jdbc.listForChat(chatId, statusFilter, limit).stream().map(ExportJobRepository::map).toList();
    }

    public List<ExportJobRow> listRecent(String statusFilter, UUID chatIdFilter, int limit) {
        return jdbc.listRecent(statusFilter, chatIdFilter, limit).stream().map(ExportJobRepository::map).toList();
    }

    public boolean cancelIfActive(UUID jobId, UUID chatId) {
        return jdbc.cancelIfActive(jobId, chatId);
    }

    public boolean cancelIfQueued(UUID jobId, UUID chatId) {
        return jdbc.cancelIfQueued(jobId, chatId);
    }

    public void markProcessing(UUID jobId) {
        jdbc.markProcessing(jobId);
    }

    public void markTerminal(UUID jobId, String status, String outputPath) {
        jdbc.markTerminal(jobId, status, outputPath);
    }

    public void markTerminal(UUID jobId, String status, String outputPath, Boolean messageTtlFilterApplied) {
        jdbc.markTerminal(jobId, status, outputPath, messageTtlFilterApplied);
    }

    public boolean applyCompleteIfPending(UUID jobId, String status, String outputPath) {
        return jdbc.applyCompleteIfPending(jobId, status, outputPath);
    }

    public boolean applyCompleteIfPending(UUID jobId, String status, String outputPath, Boolean messageTtlFilterApplied) {
        return jdbc.applyCompleteIfPending(jobId, status, outputPath, messageTtlFilterApplied);
    }

    public boolean existsCompletedExport(UUID chatId) {
        return jdbc.existsCompletedExport(chatId);
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

    private static ExportJobRow map(JdbcExportJobJdbcRepository.ExportJobRow row) {
        return new ExportJobRow(row.id(), row.chatId(), row.requestedBy(), row.status(), row.outputPath(),
            row.messageTtlFilterApplied(), row.createdAt(), row.updatedAt(), row.completedAt());
    }
}
