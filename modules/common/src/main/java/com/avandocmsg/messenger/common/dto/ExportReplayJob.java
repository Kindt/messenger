package com.avandocmsg.messenger.common.dto;

/**
 * Minimal export replay job JSON on {@link com.avandocmsg.messenger.common.nats.NatsSubjects#MSG_EXPORT_REPLAY}.
 */
public record ExportReplayJob(String jobId, String chatId, String requestedBy) {
}
