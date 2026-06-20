package com.avandocmsg.messenger.api.polls.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Poll with aggregated results")
public record PollResponse(
    String id,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("created_by") String createdBy,
    String question,
    List<String> options,
    @JsonProperty("allow_multiple") boolean allowMultiple,
    @JsonProperty("closes_at") String closesAt,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("vote_counts") List<Integer> voteCounts,
    boolean closed
) {}
