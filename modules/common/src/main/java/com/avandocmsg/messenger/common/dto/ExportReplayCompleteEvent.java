package com.avandocmsg.messenger.common.dto;

/**
 * Published to {@link com.avandocmsg.messenger.common.nats.NatsSubjects#MSG_EXPORT_REPLAY_COMPLETE} after the worker
 * finishes writing the export file. {@code status} examples: {@code export_v1} (DB-backed JSON), {@code stub_written}
 * (no {@code DB_JDBC_URL}), {@code export_failed}.
 */
public record ExportReplayCompleteEvent(
    String jobId,
    String chatId,
    String status,
    String outputPath,
    Boolean messageTtlFilterApplied
) {
}
