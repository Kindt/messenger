package com.avandocmsg.messenger.api.admin.fleet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record FleetSnapshotResponse(
    @JsonProperty("generated_at") Instant generatedAt,
    @JsonProperty("aggregator_node") String aggregatorNode,
    @JsonProperty("probe_timeout_ms") int probeTimeoutMs,
    List<FleetComponentSnapshot> components,
    @JsonProperty("shared_data") SharedData sharedData
) {
    public record FleetComponentSnapshot(
        String id,
        String role,
        String source,
        @JsonProperty("base_url") String baseUrl,
        @JsonProperty("probe_url") String probeUrl,
        boolean reachable,
        Boolean ready,
        @JsonProperty("http_status") Integer httpStatus,
        @JsonProperty("latency_ms") Long latencyMs,
        @JsonProperty("error") String error,
        @JsonProperty("hotplug_state") String hotplugState,
        @JsonProperty("hotplug_uptime_ms") Long hotplugUptimeMs,
        @JsonProperty("last_heartbeat_ms") Long lastHeartbeatMs,
        @JsonProperty("jvm_uptime_ms") Long jvmUptimeMs,
        @JsonProperty("heap_used_bytes") Long heapUsedBytes,
        Dependencies dependencies
    ) {}

    public record Dependencies(
        @JsonProperty("database_ok") Boolean databaseOk,
        @JsonProperty("redis_ok") Boolean redisOk,
        @JsonProperty("nats_ok") Boolean natsOk
    ) {}

    public record SharedData(
        @JsonProperty("api_version") String apiVersion,
        @JsonProperty("database_counts") DatabaseCounts databaseCounts
    ) {}

    public record DatabaseCounts(
        long users,
        long chats,
        long messages,
        @JsonProperty("counts_available") boolean countsAvailable
    ) {}
}
