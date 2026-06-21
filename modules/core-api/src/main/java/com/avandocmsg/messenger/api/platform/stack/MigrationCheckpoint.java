package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record MigrationCheckpoint(
    @JsonProperty("component") String component,
    @JsonProperty("source_profile") String sourceProfile,
    @JsonProperty("target_profile") String targetProfile,
    @JsonProperty("checkpoint_type") String checkpointType,
    @JsonProperty("markers") Map<String, String> markers,
    @JsonProperty("rollback_profile") String rollbackProfile,
    @JsonProperty("watch_window") String watchWindow
) {
    public MigrationCheckpoint {
        markers = markers == null ? Map.of() : Map.copyOf(markers);
    }
}
