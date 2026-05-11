package com.avandocmsg.messenger.api.export.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportAcceptedResponse(
    @JsonProperty("job_id") String jobId,
    @JsonProperty("chat_id") String chatId,
    String status
) {}
