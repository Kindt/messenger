package com.avandocmsg.messenger.api.phase5.dto;

import com.avandocmsg.messenger.api.phase5.Phase5AdrRepository;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record KanbanTaskResponse(
    @JsonProperty("task_id") String taskId,
    @JsonProperty("column_key") String columnKey,
    String title,
    @JsonProperty("created_at") Instant createdAt
) {
    public static KanbanTaskResponse created(String id, String column, String title) {
        return new KanbanTaskResponse(id, column, title, Instant.now());
    }

    public static KanbanTaskResponse from(Phase5AdrRepository.KanbanTaskRow row) {
        return new KanbanTaskResponse(
            row.id().toString(),
            row.columnKey(),
            row.title(),
            row.createdAt());
    }
}
