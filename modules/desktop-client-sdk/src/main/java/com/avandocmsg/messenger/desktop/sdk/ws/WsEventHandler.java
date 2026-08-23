package com.avandocmsg.messenger.desktop.sdk.ws;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;

/** Parses WS JSON envelopes from core-api realtime bus. */
@FunctionalInterface
public interface WsEventHandler {

    WsEventHandler NOOP = json -> {};

    void onRawMessage(String json);

    static boolean shouldRefreshTimeline(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode node = JsonSupport.mapper().readTree(json);
            if (node.has("messageId") && node.has("chatId")) {
                return true;
            }
            if (node.has("change") && node.has("messageId") && node.has("chatId")) {
                return true;
            }
            if (node.has("type") && "read_receipt".equals(node.get("type").asText())) {
                return false;
            }
            return node.has("chat_id") && node.has("user_id") && node.has("ts");
        } catch (Exception ignored) {
            return false;
        }
    }
}
