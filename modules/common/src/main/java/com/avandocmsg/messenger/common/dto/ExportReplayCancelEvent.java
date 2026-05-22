package com.avandocmsg.messenger.common.dto;

/**
 * Published to {@link com.avandocmsg.messenger.common.nats.NatsSubjects#MSG_EXPORT_REPLAY_CANCEL}
 * when an export job is cancelled (hint for workers to poll DB).
 */
public record ExportReplayCancelEvent(
    String jobId,
    String chatId,
    long cancelledAtEpochMs
) {}
