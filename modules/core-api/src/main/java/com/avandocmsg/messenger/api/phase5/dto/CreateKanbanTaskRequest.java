package com.avandocmsg.messenger.api.phase5.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateKanbanTaskRequest(
    @JsonProperty("column_key") String columnKey,
    String title
) {}
