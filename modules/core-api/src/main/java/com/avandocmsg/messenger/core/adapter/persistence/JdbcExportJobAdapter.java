package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.ExportJobRepository;
import com.avandocmsg.messenger.core.port.ExportJobPort;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcExportJobAdapter implements ExportJobPort {
    private final ExportJobRepository delegate;

    public JdbcExportJobAdapter(ExportJobRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcExportJobAdapter(DataSource dataSource) {
        this.delegate = new ExportJobRepository(dataSource);
    }

    @Override
    public void insertQueued(UUID jobId, UUID chatId, UUID requestedBy) {
        delegate.insertQueued(jobId, chatId, requestedBy);
    }

    @Override
    public Optional<ExportJobRow> findLatestForChat(UUID chatId) {
        return delegate.findLatestForChat(chatId).map(JdbcExportJobAdapter::map);
    }

    @Override
    public Optional<ExportJobRow> findLatestCompletedExport(UUID chatId) {
        return delegate.findLatestCompletedExport(chatId).map(JdbcExportJobAdapter::map);
    }

    @Override
    public boolean isExportSufficientForPurge(UUID chatId) {
        return delegate.isExportSufficientForPurge(chatId);
    }

    @Override
    public boolean hasBlockingJobForChat(UUID chatId, int cooldownMinutes) {
        return delegate.hasBlockingJobForChat(chatId, cooldownMinutes);
    }

    @Override
    public Optional<ExportJobRow> findByIdAndChat(UUID jobId, UUID chatId) {
        return delegate.findByIdAndChat(jobId, chatId).map(JdbcExportJobAdapter::map);
    }

    @Override
    public List<ExportJobRow> listForChat(UUID chatId, String statusFilter, int limit) {
        return delegate.listForChat(chatId, statusFilter, limit).stream().map(JdbcExportJobAdapter::map).toList();
    }

    @Override
    public List<ExportJobRow> listRecent(String statusFilter, UUID chatIdFilter, int limit) {
        return delegate.listRecent(statusFilter, chatIdFilter, limit).stream().map(JdbcExportJobAdapter::map).toList();
    }

    @Override
    public boolean cancelIfActive(UUID jobId, UUID chatId) {
        return delegate.cancelIfActive(jobId, chatId);
    }

    @Override
    public boolean cancelIfQueued(UUID jobId, UUID chatId) {
        return delegate.cancelIfQueued(jobId, chatId);
    }

    @Override
    public void markProcessing(UUID jobId) {
        delegate.markProcessing(jobId);
    }

    @Override
    public void markTerminal(UUID jobId, String status, String outputPath) {
        delegate.markTerminal(jobId, status, outputPath);
    }

    @Override
    public void markTerminal(UUID jobId, String status, String outputPath, Boolean messageTtlFilterApplied) {
        delegate.markTerminal(jobId, status, outputPath, messageTtlFilterApplied);
    }

    @Override
    public boolean applyCompleteIfPending(UUID jobId, String status, String outputPath) {
        return delegate.applyCompleteIfPending(jobId, status, outputPath);
    }

    @Override
    public boolean applyCompleteIfPending(UUID jobId, String status, String outputPath, Boolean messageTtlFilterApplied) {
        return delegate.applyCompleteIfPending(jobId, status, outputPath, messageTtlFilterApplied);
    }

    @Override
    public boolean existsCompletedExport(UUID chatId) {
        return delegate.existsCompletedExport(chatId);
    }

    private static ExportJobRow map(ExportJobRepository.ExportJobRow row) {
        return new ExportJobRow(row.id(), row.chatId(), row.requestedBy(), row.status(), row.outputPath(),
            row.messageTtlFilterApplied(), row.createdAt(), row.updatedAt(), row.completedAt());
    }
}
