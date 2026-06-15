package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Optional;

/** JSON helpers for read-cache user profile blobs. */
public final class ReadCacheJson {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private ReadCacheJson() {
    }
    public static Optional<String> userProfileToJson(UserProfile profile) {
        try {
            var node = MAPPER.createObjectNode();
            node.put("id", profile.id().value().toString());
            node.put("username", profile.username());
            node.put("displayName", profile.displayName());
            node.put("phone", profile.phone());
            node.put("hidden", profile.hidden());
            if (profile.createdAt() != null) {
                node.put("createdAt", profile.createdAt().toString());
            }
            node.put("presenceStatus", profile.presenceStatus());
            if (profile.lastSeenAt() != null) {
                node.put("lastSeenAt", profile.lastSeenAt().toString());
            }
            node.put("orgId", profile.orgId());
            node.put("privacyDisableReadReceipts", profile.privacyDisableReadReceipts());
            node.put("uiLocale", profile.uiLocale());
            return Optional.of(MAPPER.writeValueAsString(node));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<UserProfile> userProfileFromJson(String json) {
        try {
            var node = MAPPER.readTree(json);
            var id = UserId.of(java.util.UUID.fromString(node.get("id").asText()));
            var createdAt = node.hasNonNull("createdAt")
                ? java.time.Instant.parse(node.get("createdAt").asText()) : null;
            var lastSeen = node.hasNonNull("lastSeenAt")
                ? java.time.Instant.parse(node.get("lastSeenAt").asText()) : null;
            return Optional.of(new UserProfile(
                id,
                textOrNull(node, "username"),
                textOrNull(node, "displayName"),
                textOrNull(node, "phone"),
                node.path("hidden").asBoolean(false),
                createdAt,
                textOrNull(node, "presenceStatus"),
                lastSeen,
                textOrNull(node, "orgId"),
                node.path("privacyDisableReadReceipts").asBoolean(false),
                textOrNull(node, "uiLocale")));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String textOrNull(com.fasterxml.jackson.databind.JsonNode node, String field) {
        var n = node.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
