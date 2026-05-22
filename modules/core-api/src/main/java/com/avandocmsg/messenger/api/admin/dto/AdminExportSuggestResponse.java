package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminExportSuggestResponse(
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("dispatch") String dispatch,
    @JsonProperty("reason") String reason,
    @JsonProperty("candidate_message_count") int candidateMessageCount,
    @JsonProperty("suggested_at_epoch_ms") long suggestedAtEpochMs,
    @JsonProperty("auto_queued_job_id") String autoQueuedJobId
) {}
