package com.avandocmsg.messenger.worker.push;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Subscription;

import java.util.Optional;

final class WebPushSubscriptionParser {

    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private WebPushSubscriptionParser() {
    }

    static Optional<Subscription> parse(String pushToken) {
        if (pushToken == null || pushToken.isBlank()) {
            return Optional.empty();
        }
        var trimmed = pushToken.trim();
        if (!trimmed.startsWith("{")) {
            return Optional.empty();
        }
        try {
            var node = MAPPER.readTree(trimmed);
            var endpoint = node.path("endpoint").asText(null);
            var keys = node.path("keys");
            var p256dh = keys.path("p256dh").asText(null);
            var auth = keys.path("auth").asText(null);
            if (endpoint == null || p256dh == null || auth == null) {
                return Optional.empty();
            }
            return Optional.of(new Subscription(endpoint, new Subscription.Keys(p256dh, auth)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
