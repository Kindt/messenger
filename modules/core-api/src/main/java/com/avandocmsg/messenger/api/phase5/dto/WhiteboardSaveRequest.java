package com.avandocmsg.messenger.api.phase5.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WhiteboardSaveRequest(String title, @JsonProperty("snapshot_json") String snapshotJson) {}
