package com.avandocmsg.messenger.common.dto;

/**
 * Published to {@link com.avandocmsg.messenger.common.nats.NatsSubjects#MSG_EXPORT_REPLAY_COMPLETE} after stub processing.
 */
public record ExportReplayCompleteEvent(String jobId, String chatId, String status, String outputPath) {
}
