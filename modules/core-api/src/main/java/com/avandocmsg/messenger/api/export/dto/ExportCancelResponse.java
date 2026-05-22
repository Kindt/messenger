package com.avandocmsg.messenger.api.export.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExportCancelResponse(
    @JsonProperty("job_id") String jobId,
    @JsonProperty("chat_id") String chatId,
    String status,
    boolean cancelled
) {}
