package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * WebRTC signaling fan-out over NATS / {@code msg.deliver.*} (same transport as chat messages).
 * Client → ws-gateway (WebSocket) → {@code rtc.signal} → message-pipeline → {@code msg.deliver.{peer}}.
 */
public record RtcSignalEvent(
    String type,
    String chatId,
    String fromUserId,
    JsonNode payload
) {
    public static final String TYPE = "rtc_signal"; // NOSONAR java:S1845 -- wire constant matches JSON field name type

    public RtcSignalEvent(String chatId, String fromUserId, JsonNode payload) {
        this(TYPE, chatId, fromUserId, payload);
    }
}
