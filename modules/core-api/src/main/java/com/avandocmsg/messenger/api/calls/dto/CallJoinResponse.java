package com.avandocmsg.messenger.api.calls.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CallJoinResponse(
    @JsonProperty("session_id") String sessionId,
    @JsonProperty("participant_id") String participantId,
    @JsonProperty("chat_id") String chatId,
    String kind,
    String role,
    String status,
    @JsonProperty("media_node_id") String mediaNodeId,
    @JsonProperty("signaling_path") String signalingPath,
    @JsonProperty("ice_servers") List<IceServerResponse> iceServers
) {
    public record IceServerResponse(List<String> urls, String username, String credential) {}
}
