package com.avandocmsg.messenger.api.polls.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Vote on poll")
public record VotePollRequest(
    @Schema(description = "Selected option indexes (0-based)") @JsonProperty("option_indexes") List<Integer> optionIndexes
) {}
