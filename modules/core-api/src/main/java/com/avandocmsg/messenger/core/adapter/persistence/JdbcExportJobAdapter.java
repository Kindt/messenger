package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.ExportJobRepository;
import com.avandocmsg.messenger.core.port.ExportJobPort;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcExportJobAdapter implements ExportJobPort {
    private final JdbcExportJobJdbcRepository jdbc;
    private final ExportJobRepository legacy;

    public JdbcExportJobAdapter(JdbcExportJobJdbcRepository jdbc) {
        this.jdbc = jdbc;
        this.legacy = null;
    }

    public JdbcExportJobAdapter(DataSource dataSource) {
        this(new JdbcExportJobJdbcRepository(dataSource));
    }

    /** Legacy/test delegate (in-memory stubs extending {@link ExportJobRepository}). */
    public JdbcExportJobAdapter(ExportJobRepository legacy) {
        this.jdbc = null;
        this.legacy = legacy;
    }

    @Override
    public void insertQueued(UUID jobId, UUID chatId, UUID requestedBy) {
        if (usesLegacy()) {
            legacy.insertQueued(jobId, chatId, requestedBy);
        } else {
            jdbc.insertQueued(jobId, chatId, requestedBy);
        }
    }

    @Override
    public Optional<ExportJobRow> findLatestForChat(UUID chatId) {
        if (usesLegacy()) {
            return legacy.findLatestForChat(chatId).map(JdbcExportJobAdapter::mapLegacy);
        }
        return jdbc.findLatestForChat(chatId).map(JdbcExportJobAdapter::map);
    }

    @Override
    public Optional<ExportJobRow> findLatestCompletedExport(UUID chatId) {
        if (usesLegacy()) {
            return legacy.findLatestCompletedExport(chatId).map(JdbcExportJobAdapter::mapLegacy);
        }
        return jdbc.findLatestCompletedExport(chatId).map(JdbcExportJobAdapter::map);
    }

    @Override
    public boolean isExportSufficientForPurge(UUID chatId) {
        return usesLegacy() ? legacy.isExportSufficientForPurge(chatId) : jdbc.isExportSufficientForPurge(chatId);
    }

    @Override
    public boolean hasBlockingJobForChat(UUID chatId, int cooldownMinutes) {
        return usesLegacy()
            ? legacy.hasBlockingJobForChat(chatId, cooldownMinutes)
            : jdbc.hasBlockingJobForChat(chatId, cooldownMinutes);
    }

    @Override
    public Optional<ExportJobRow> findByIdAndChat(UUID jobId, UUID chatId) {
        if (usesLegacy()) {
            return legacy.findByIdAndChat(jobId, chatId).map(JdbcExportJobAdapter::mapLegacy);
        }
        return jdbc.findByIdAndChat(jobId, chatId).map(JdbcExportJobAdapter::map);
    }

    @Override
    public List<ExportJobRow> listForChat(UUID chatId, String statusFilter, int limit) {
        if (usesLegacy()) {
            return legacy.listForChat(chatId, statusFilter, limit).stream().map(JdbcExportJobAdapter::mapLegacy).toList();
        }
        return jdbc.listForChat(chatId, statusFilter, limit).stream().map(JdbcExportJobAdapter::map).toList();
    }

    @Override
    public List<ExportJobRow> listRecent(String statusFilter, UUID chatIdFilter, int limit) {
        if (usesLegacy()) {
            return legacy.listRecent(statusFilter, chatIdFilter, limit).stream().map(JdbcExportJobAdapter::mapLegacy).toList();
        }
        return jdbc.listRecent(statusFilter, chatIdFilter, limit).stream().map(JdbcExportJobAdapter::map).toList();
    }

    @Override
    public boolean cancelIfActive(UUID jobId, UUID chatId) {
        return usesLegacy() ? legacy.cancelIfActive(jobId, chatId) : jdbc.cancelIfActive(jobId, chatId);
    }

    @Override
    public boolean cancelIfQueued(UUID jobId, UUID chatId) {
        return usesLegacy() ? legacy.cancelIfQueued(jobId, chatId) : jdbc.cancelIfQueued(jobId, chatId);
    }

    @Override
    public void markProcessing(UUID jobId) {
        if (usesLegacy()) {
            legacy.markProcessing(jobId);
        } else {
            jdbc.markProcessing(jobId);
        }
    }

    @Override
    public void markTerminal(UUID jobId, String status, String outputPath) {
        if (usesLegacy()) {
            legacy.markTerminal(jobId, status, outputPath);
        } else {
            jdbc.markTerminal(jobId, status, outputPath);
        }
    }

    @Override
    public void markTerminal(UUID jobId, String status, String outputPath, Boolean messageTtlFilterApplied) {
        if (usesLegacy()) {
            legacy.markTerminal(jobId, status, outputPath, messageTtlFilterApplied);
        } else {
            jdbc.markTerminal(jobId, status, outputPath, messageTtlFilterApplied);
        }
    }

    @Override
    public boolean applyCompleteIfPending(UUID jobId, String status, String outputPath) {
        return usesLegacy()
            ? legacy.applyCompleteIfPending(jobId, status, outputPath)
            : jdbc.applyCompleteIfPending(jobId, status, outputPath);
    }

    @Override
    public boolean applyCompleteIfPending(UUID jobId, String status, String outputPath, Boolean messageTtlFilterApplied) {
        return usesLegacy()
            ? legacy.applyCompleteIfPending(jobId, status, outputPath, messageTtlFilterApplied)
            : jdbc.applyCompleteIfPending(jobId, status, outputPath, messageTtlFilterApplied);
    }

    @Override
    public boolean existsCompletedExport(UUID chatId) {
        return usesLegacy() ? legacy.existsCompletedExport(chatId) : jdbc.existsCompletedExport(chatId);
    }

    private boolean usesLegacy() {
        return legacy != null;
    }

    private static ExportJobRow map(JdbcExportJobJdbcRepository.ExportJobRow row) {
        return new ExportJobRow(row.id(), row.chatId(), row.requestedBy(), row.status(), row.outputPath(),
            row.messageTtlFilterApplied(), row.createdAt(), row.updatedAt(), row.completedAt());
    }

    private static ExportJobRow mapLegacy(ExportJobRepository.ExportJobRow row) {
        return new ExportJobRow(row.id(), row.chatId(), row.requestedBy(), row.status(), row.outputPath(),
            row.messageTtlFilterApplied(), row.createdAt(), row.updatedAt(), row.completedAt());
    }
}
