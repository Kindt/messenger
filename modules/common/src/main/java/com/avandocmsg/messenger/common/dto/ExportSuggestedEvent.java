package com.avandocmsg.messenger.common.dto;

/**
 * Published to {@link com.avandocmsg.messenger.common.nats.NatsSubjects#MSG_EXPORT_SUGGESTED} when retention
 * is about to clear hot message bodies for a chat (compliance hint; does not auto-queue export).
 */
public record ExportSuggestedEvent(
    String chatId,
    String reason,
    int candidateMessageCount,
    long suggestedAtEpochMs
) {
    public static final String REASON_HOT_BODY_CANDIDATES = "hot_body_candidates";
}
