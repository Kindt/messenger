package com.avandocmsg.messenger.api.scim;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.OrgUserDirectoryPort;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScimUserMapper {
    static final List<String> USER_SCHEMA = List.of("urn:ietf:params:scim:schemas:core:2.0:User");
    static final List<String> LIST_SCHEMA = List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse");

    private static final String FIELD_USER_NAME = "userName";
    private static final String FIELD_EXTERNAL_ID = "externalId";
    private static final String FIELD_ACTIVE = "active";
    private static final String FIELD_DISPLAY_NAME = "displayName";
    private static final String FIELD_OPERATIONS = "Operations";
    private static final String FIELD_VALUE = "value";
    private static final String FIELD_EMAILS = "emails";

    private ScimUserMapper() {}

    static ScimUserResource toResource(OrgUserDirectoryPort.OrgDirectoryUser profile, String baseLocation) {
        var emails = profile.email() != null && !profile.email().isBlank()
            ? List.of(new ScimEmail(profile.email(), true))
            : List.<ScimEmail>of();
        return new ScimUserResource(
            USER_SCHEMA,
            profile.id().toString(),
            profile.externalId(),
            profile.username(),
            profile.displayName(),
            !profile.hidden(),
            emails.isEmpty() ? null : emails,
            new ScimMeta("User", baseLocation + profile.id()));
    }

    static ScimListResponse toList(List<OrgUserDirectoryPort.OrgDirectoryUser> profiles, int total, int startIndex, String baseLocation) {
        var resources = new ArrayList<ScimUserResource>();
        for (var p : profiles) {
            resources.add(toResource(p, baseLocation));
        }
        return new ScimListResponse(LIST_SCHEMA, total, startIndex, resources.size(), List.copyOf(resources));
    }

    static ParsedCreate parseCreate(JsonNode body) {
        var userName = text(body, FIELD_USER_NAME);
        var externalId = text(body, FIELD_EXTERNAL_ID);
        var active = body.path(FIELD_ACTIVE).asBoolean(true);
        var email = primaryEmail(body);
        var displayName = firstNonBlank(text(body, FIELD_DISPLAY_NAME), userName);
        return new ParsedCreate(userName, externalId, email, displayName, active);
    }

    static ParsedPatch parsePatch(JsonNode body) {
        var state = new PatchState(
            text(body, FIELD_USER_NAME),
            text(body, FIELD_EXTERNAL_ID),
            body.has(FIELD_ACTIVE) ? body.path(FIELD_ACTIVE).asBoolean() : null,
            primaryEmail(body),
            text(body, FIELD_DISPLAY_NAME)
        );
        if (body.has(FIELD_OPERATIONS) && body.get(FIELD_OPERATIONS).isArray()) {
            for (var op : body.get(FIELD_OPERATIONS)) {
                applyReplaceOp(state, op);
            }
        }
        return new ParsedPatch(state.userName, state.externalId, state.email, state.displayName, state.active);
    }

    private static void applyReplaceOp(PatchState state, JsonNode op) {
        if (!"replace".equalsIgnoreCase(op.path("op").asText("replace"))) {
            return;
        }
        var path = op.path("path").asText("");
        var value = op.get(FIELD_VALUE);
        if (path.isBlank() && value != null && value.isObject()) {
            applyObjectReplace(state, value);
            return;
        }
        if (value == null) {
            return;
        }
        if (FIELD_USER_NAME.equalsIgnoreCase(path)) {
            state.userName = value.asText();
        } else if (FIELD_EXTERNAL_ID.equalsIgnoreCase(path)) {
            state.externalId = value.asText();
        } else if (FIELD_ACTIVE.equalsIgnoreCase(path)) {
            state.active = value.asBoolean();
        } else if (FIELD_EMAILS.equalsIgnoreCase(path)) {
            state.email = primaryEmail(value);
        } else if (FIELD_DISPLAY_NAME.equalsIgnoreCase(path)) {
            state.displayName = value.asText();
        }
    }

    private static void applyObjectReplace(PatchState state, JsonNode value) {
        state.userName = firstNonBlank(text(value, FIELD_USER_NAME), state.userName);
        state.externalId = firstNonBlank(text(value, FIELD_EXTERNAL_ID), state.externalId);
        if (value.has(FIELD_ACTIVE)) {
            state.active = value.path(FIELD_ACTIVE).asBoolean();
        }
        state.email = firstNonBlank(primaryEmail(value), state.email);
        state.displayName = firstNonBlank(text(value, FIELD_DISPLAY_NAME), state.displayName);
    }

    static UUID defaultOrgId(AppConfig appConfig) {
        return appConfig.defaultOrgId().orElse(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    }

    private static String primaryEmail(JsonNode body) {
        if (!body.has(FIELD_EMAILS) || !body.get(FIELD_EMAILS).isArray()) {
            return null;
        }
        for (var node : body.get(FIELD_EMAILS)) {
            if (node.path("primary").asBoolean(true)) {
                var value = node.path(FIELD_VALUE).asText(null);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        if (body.get(FIELD_EMAILS).size() > 0) {
            return body.get(FIELD_EMAILS).get(0).path(FIELD_VALUE).asText(null);
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

    private static final class PatchState {
        String userName;
        String externalId;
        Boolean active;
        String email;
        String displayName;

        PatchState(String userName, String externalId, Boolean active, String email, String displayName) {
            this.userName = userName;
            this.externalId = externalId;
            this.active = active;
            this.email = email;
            this.displayName = displayName;
        }
    }

    record ParsedCreate(String userName, String externalId, String email, String displayName, boolean active) {}
    record ParsedPatch(String userName, String externalId, String email, String displayName, Boolean active) {}
}
