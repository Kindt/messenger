package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CallJoinResponse(
    @JsonProperty("session_id") String sessionId,
    @JsonProperty("participant_id") String participantId,
    @JsonProperty("chat_id") String chatId,
    String kind,
    String role,
    String status,
    @JsonProperty("media_node_id") String mediaNodeId,
    @JsonProperty("signaling_path") String signalingPath,
    @JsonProperty("ice_servers") List<CallIceServer> iceServers
) {
    public record CallIceServer(List<String> urls, String username, String credential) {}
}
