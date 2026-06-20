package com.avandocmsg.messenger.api.polls.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Create in-chat poll")
public record CreatePollRequest(
    @Schema(description = "Poll question") String question,
    @Schema(description = "Answer options") List<String> options,
    @Schema(description = "Allow selecting multiple options") @JsonProperty("allow_multiple") Boolean allowMultiple,
    @Schema(description = "Poll close time (ISO-8601)") @JsonProperty("closes_at") String closesAt
) {}
