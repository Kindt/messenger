package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminExportSuggestRequest(
    @JsonProperty("candidate_message_count") Integer candidateMessageCount,
    @JsonProperty("reason") String reason,
    /** {@code local} (default), {@code nats}, or {@code both}. */
    @JsonProperty("dispatch") String dispatch
) {}
