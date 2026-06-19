package com.avandocmsg.messenger.core.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Export job queue ({@code export_jobs}). */
public interface ExportJobPort {
    void insertQueued(UUID jobId, UUID chatId, UUID requestedBy);

    Optional<ExportJobRow> findLatestForChat(UUID chatId);

    Optional<ExportJobRow> findLatestCompletedExport(UUID chatId);

    boolean isExportSufficientForPurge(UUID chatId);

    boolean hasBlockingJobForChat(UUID chatId, int cooldownMinutes);

    Optional<ExportJobRow> findByIdAndChat(UUID jobId, UUID chatId);

    List<ExportJobRow> listForChat(UUID chatId, String statusFilter, int limit);

    List<ExportJobRow> listRecent(String statusFilter, UUID chatIdFilter, int limit);

    boolean cancelIfActive(UUID jobId, UUID chatId);

    boolean cancelIfQueued(UUID jobId, UUID chatId);

    void markProcessing(UUID jobId);

    void markTerminal(UUID jobId, String status, String outputPath);

    void markTerminal(UUID jobId, String status, String outputPath, Boolean messageTtlFilterApplied);

    boolean applyCompleteIfPending(UUID jobId, String status, String outputPath);

    boolean applyCompleteIfPending(UUID jobId, String status, String outputPath, Boolean messageTtlFilterApplied);

    boolean existsCompletedExport(UUID chatId);

    record ExportJobRow(
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
