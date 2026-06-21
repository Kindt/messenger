package com.avandocmsg.messenger.api.phase5.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateKanbanTaskRequest(
    @JsonProperty("column_key") String columnKey,
    @JsonProperty("sort_order") Integer sortOrder,
    String title
) {}
