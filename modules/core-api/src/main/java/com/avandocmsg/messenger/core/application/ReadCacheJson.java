package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;

import java.util.Optional;

/** JSON helpers for read-cache user profile blobs. */
public final class ReadCacheJson {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = MessengerJson.mapper();
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_SEEN_AT = "lastSeenAt";
    private static final String FIELD_DND_UNTIL = "dndUntil";
    private static final String FIELD_AVATAR_FILE_ID = "avatarFileId";

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
                node.put(FIELD_CREATED_AT, profile.createdAt().toString());
            }
            node.put("presenceStatus", profile.presenceStatus());
            if (profile.lastSeenAt() != null) {
                node.put(FIELD_LAST_SEEN_AT, profile.lastSeenAt().toString());
            }
            node.put("orgId", profile.orgId());
            node.put("privacyDisableReadReceipts", profile.privacyDisableReadReceipts());
            node.put("uiLocale", profile.uiLocale());
            node.put("customStatusText", profile.customStatusText());
            if (profile.dndUntil() != null) {
                node.put(FIELD_DND_UNTIL, profile.dndUntil().toString());
            }
            if (profile.avatarFileId() != null) {
                node.put(FIELD_AVATAR_FILE_ID, profile.avatarFileId().value().toString());
            }
            node.put("avatarHidden", profile.avatarHidden());
            return Optional.of(MAPPER.writeValueAsString(node));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<UserProfile> userProfileFromJson(String json) {
        try {
            var node = MAPPER.readTree(json);
            var id = UserId.of(java.util.UUID.fromString(node.get("id").asText()));
            var createdAt = node.hasNonNull(FIELD_CREATED_AT)
                ? java.time.Instant.parse(node.get(FIELD_CREATED_AT).asText()) : null;
            var lastSeen = node.hasNonNull(FIELD_LAST_SEEN_AT)
                ? java.time.Instant.parse(node.get(FIELD_LAST_SEEN_AT).asText()) : null;
            var dndUntil = node.hasNonNull(FIELD_DND_UNTIL)
                ? java.time.Instant.parse(node.get(FIELD_DND_UNTIL).asText()) : null;
            com.avandocmsg.messenger.core.domain.FileId avatarFileId = null;
            if (node.hasNonNull(FIELD_AVATAR_FILE_ID)) {
                avatarFileId = com.avandocmsg.messenger.core.domain.FileId.of(
                    java.util.UUID.fromString(node.get(FIELD_AVATAR_FILE_ID).asText()));
            }
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
                textOrNull(node, "uiLocale"),
                textOrNull(node, "customStatusText"),
                dndUntil,
                node.path("avatarHidden").asBoolean(false),
                avatarFileId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String textOrNull(com.fasterxml.jackson.databind.JsonNode node, String field) {
        var n = node.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
