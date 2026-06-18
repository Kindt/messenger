package com.avandocmsg.messenger.api.scim;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScimUserMapper {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final List<String> USER_SCHEMA = List.of("urn:ietf:params:scim:schemas:core:2.0:User");
    static final List<String> LIST_SCHEMA = List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse");

    private ScimUserMapper() {}

    static ScimUserResource toResource(UserProfile profile, String baseLocation) {
        var emails = profile.email() != null && !profile.email().isBlank()
            ? List.of(new ScimEmail(profile.email(), true))
            : List.<ScimEmail>of();
        return new ScimUserResource(
            USER_SCHEMA,
            profile.id(),
            profile.externalId(),
            profile.username(),
            profile.displayName(),
            !profile.hidden(),
            emails.isEmpty() ? null : emails,
            new ScimMeta("User", baseLocation + profile.id()));
    }

    static ScimListResponse toList(List<UserProfile> profiles, int total, int startIndex, String baseLocation) {
        var resources = new ArrayList<ScimUserResource>();
        for (var p : profiles) {
            resources.add(toResource(p, baseLocation));
        }
        return new ScimListResponse(LIST_SCHEMA, total, startIndex, resources.size(), List.copyOf(resources));
    }

    static ParsedCreate parseCreate(JsonNode body) {
        var userName = text(body, "userName");
        var externalId = text(body, "externalId");
        var active = body.path("active").asBoolean(true);
        var email = primaryEmail(body);
        var displayName = firstNonBlank(text(body, "displayName"), userName);
        return new ParsedCreate(userName, externalId, email, displayName, active);
    }

    static ParsedPatch parsePatch(JsonNode body) {
        var userName = text(body, "userName");
        var externalId = text(body, "externalId");
        Boolean active = body.has("active") ? body.path("active").asBoolean() : null;
        var email = primaryEmail(body);
        var displayName = text(body, "displayName");

        if (body.has("Operations") && body.get("Operations").isArray()) {
            for (var op : body.get("Operations")) {
                var path = op.path("path").asText("");
                var value = op.get("value");
                if ("replace".equalsIgnoreCase(op.path("op").asText("replace"))) {
                    if (path.isBlank() && value != null && value.isObject()) {
                        userName = firstNonBlank(text(value, "userName"), userName);
                        externalId = firstNonBlank(text(value, "externalId"), externalId);
                        if (value.has("active")) {
                            active = value.path("active").asBoolean();
                        }
                        email = firstNonBlank(primaryEmail(value), email);
                        displayName = firstNonBlank(text(value, "displayName"), displayName);
                    } else if ("userName".equalsIgnoreCase(path) && value != null) {
                        userName = value.asText();
                    } else if ("externalId".equalsIgnoreCase(path) && value != null) {
                        externalId = value.asText();
                    } else if ("active".equalsIgnoreCase(path) && value != null) {
                        active = value.asBoolean();
                    } else if ("emails".equalsIgnoreCase(path) && value != null) {
                        email = primaryEmail(value);
                    } else if ("displayName".equalsIgnoreCase(path) && value != null) {
                        displayName = value.asText();
                    }
                }
            }
        }
        return new ParsedPatch(userName, externalId, email, displayName, active);
    }

    static UUID defaultOrgId(AppConfig appConfig) {
        return appConfig.defaultOrgId().orElse(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    }

    private static String primaryEmail(JsonNode body) {
        if (body.has("emails") && body.get("emails").isArray()) {
            for (var node : body.get("emails")) {
                if (node.path("primary").asBoolean(true)) {
                    var value = node.path("value").asText(null);
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
            if (body.get("emails").size() > 0) {
                return body.get("emails").get(0).path("value").asText(null);
            }
        }
        return null;
    }

    private static String text(JsonNode body, String field) {
        var v = body.path(field).asText(null);
        return v != null && !v.isBlank() ? v : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    record ParsedCreate(String userName, String externalId, String email, String displayName, boolean active) {}
    record ParsedPatch(String userName, String externalId, String email, String displayName, Boolean active) {}
}
