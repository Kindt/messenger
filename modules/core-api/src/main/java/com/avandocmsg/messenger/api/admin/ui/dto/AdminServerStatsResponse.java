package com.avandocmsg.messenger.api.admin.ui.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminServerStatsResponse(
    @JsonProperty("api_version") String apiVersion,
    @JsonProperty("jvm") JvmStats jvm,
    @JsonProperty("dependencies") DependencyHealth dependencies,
    @JsonProperty("counts") TableCounts counts
) {
    public record JvmStats(
        @JsonProperty("heap_used_bytes") long heapUsedBytes,
        @JsonProperty("heap_committed_bytes") long heapCommittedBytes,
        @JsonProperty("heap_max_bytes") long heapMaxBytes,
        @JsonProperty("processors") int processors,
        @JsonProperty("uptime_ms") long uptimeMs
    ) {}

    public record DependencyHealth(
        @JsonProperty("database_ok") boolean databaseOk,
        @JsonProperty("redis_ok") boolean redisOk,
        @JsonProperty("nats_ok") boolean natsOk
    ) {}

    public record TableCounts(
        @JsonProperty("users") long users,
        @JsonProperty("chats") long chats,
        @JsonProperty("messages") long messages,
        @JsonProperty("counts_available") boolean countsAvailable
    ) {}
}
