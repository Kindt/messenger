package com.avandocmsg.messenger.api.scim;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.ScimGroupRepositoryPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public class ScimGroupMapper {
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final TypeReference<List<String>> MEMBER_IDS = new TypeReference<>() {};

    private static final String ATTR_DISPLAY_NAME = "displayName";
    private static final String ATTR_EXTERNAL_ID = "externalId";
    private static final String ATTR_MEMBERS = "members";
    private static final String ATTR_OPERATIONS = "Operations";

    static final List<String> GROUP_SCHEMA = List.of("urn:ietf:params:scim:schemas:core:2.0:Group");
    static final List<String> LIST_SCHEMA = List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse");

    private ScimGroupMapper() {}

    static ScimGroupResource toResource(ScimGroupRepositoryPort.ScimGroupRow row, String baseLocation) {
        return new ScimGroupResource(
            GROUP_SCHEMA,
            row.id().toString(),
            row.externalId(),
            row.displayName(),
            toMembers(row.membersJson()),
            new ScimMeta("Group", baseLocation + row.id()));
    }

    static ScimGroupListResponse toList(
        List<ScimGroupRepositoryPort.ScimGroupRow> rows,
        int total,
        int startIndex,
        String baseLocation
    ) {
        var resources = new ArrayList<ScimGroupResource>();
        for (var row : rows) {
            resources.add(toResource(row, baseLocation));
        }
        return new ScimGroupListResponse(LIST_SCHEMA, total, startIndex, resources.size(), List.copyOf(resources));
    }

    static ParsedCreate parseCreate(JsonNode body) {
        var displayName = text(body, ATTR_DISPLAY_NAME);
        var externalId = text(body, ATTR_EXTERNAL_ID);
        var members = parseMemberIds(body.path(ATTR_MEMBERS));
        return new ParsedCreate(displayName, externalId, members);
    }

    static ParsedPatch parsePatch(JsonNode body, List<String> existingMembers) {
        var displayName = text(body, ATTR_DISPLAY_NAME);
        var externalId = text(body, ATTR_EXTERNAL_ID);
        var members = new LinkedHashSet<>(existingMembers);

        if (body.has(ATTR_MEMBERS) && body.get(ATTR_MEMBERS).isArray()) {
            members.clear();
            members.addAll(parseMemberIds(body.get(ATTR_MEMBERS)));
        }

        if (body.has(ATTR_OPERATIONS) && body.get(ATTR_OPERATIONS).isArray()) {
            for (var op : body.get(ATTR_OPERATIONS)) {
                var result = applyOperation(op, displayName, externalId, members);
                displayName = result.displayName();
                externalId = result.externalId();
            }
        }
        return new ParsedPatch(displayName, externalId, List.copyOf(members));
    }

    private static PatchAttrs applyOperation(
        JsonNode op,
        String displayName,
        String externalId,
        LinkedHashSet<String> members
    ) {
        var operation = op.path("op").asText("replace");
        var path = op.path("path").asText("");
        var value = op.get("value");
        if ("replace".equalsIgnoreCase(operation)) {
            return applyReplace(path, value, displayName, externalId, members);
        }
        if ("add".equalsIgnoreCase(operation) && path.startsWith(ATTR_MEMBERS)) {
            addMembers(value, members);
            return new PatchAttrs(displayName, externalId);
        }
        if ("remove".equalsIgnoreCase(operation) && path.startsWith(ATTR_MEMBERS)) {
            var removed = extractMemberFilter(path);
            if (removed != null) {
                members.remove(removed);
            }
        }
        return new PatchAttrs(displayName, externalId);
    }

    private static PatchAttrs applyReplace(
        String path,
        JsonNode value,
        String displayName,
        String externalId,
        LinkedHashSet<String> members
    ) {
        if (path.isBlank() && value != null && value.isObject()) {
            displayName = firstNonBlank(text(value, ATTR_DISPLAY_NAME), displayName);
            externalId = firstNonBlank(text(value, ATTR_EXTERNAL_ID), externalId);
            if (value.has(ATTR_MEMBERS)) {
                members.clear();
                members.addAll(parseMemberIds(value.get(ATTR_MEMBERS)));
            }
            return new PatchAttrs(displayName, externalId);
        }
        if (ATTR_DISPLAY_NAME.equalsIgnoreCase(path) && value != null) {
            return new PatchAttrs(value.asText(), externalId);
        }
        if (ATTR_EXTERNAL_ID.equalsIgnoreCase(path) && value != null) {
            return new PatchAttrs(displayName, value.asText());
        }
        return new PatchAttrs(displayName, externalId);
    }

    private static void addMembers(JsonNode value, LinkedHashSet<String> members) {
        if (value == null) {
            return;
        }
        if (value.isArray()) {
            members.addAll(parseMemberIds(value));
        } else if (value.isObject()) {
            members.addAll(parseMemberIds(MAPPER.createArrayNode().add(value)));
        }
    }

    static String membersJson(List<String> memberIds) {
        try {
            return MAPPER.writeValueAsString(memberIds != null ? memberIds : List.of());
        } catch (Exception e) {
            return "[]";
        }
    }

    static List<String> membersFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            var ids = MAPPER.readValue(json, MEMBER_IDS);
            return ids != null ? List.copyOf(ids) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    static UUID defaultOrgId(AppConfig appConfig) {
        return appConfig.defaultOrgId().orElse(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    }

    private static List<ScimMember> toMembers(String membersJson) {
        var ids = membersFromJson(membersJson);
        if (ids.isEmpty()) {
            return List.of();
        }
        var out = new ArrayList<ScimMember>();
        for (var id : ids) {
            out.add(new ScimMember(id));
        }
        return out;
    }

    private static List<String> parseMemberIds(JsonNode membersNode) {
        var out = new ArrayList<String>();
        if (membersNode == null || !membersNode.isArray()) {
            return out;
        }
        for (var node : membersNode) {
            var value = node.path("value").asText(null);
            if (value != null && !value.isBlank()) {
                out.add(value);
            }
        }
        return out;
    }

    private static String extractMemberFilter(String path) {
        var needle = ATTR_MEMBERS + "[value eq \"";
        var idx = path.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        var rest = path.substring(idx + needle.length());
        var end = rest.indexOf('"');
        return end >= 0 ? rest.substring(0, end) : null;
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

    private record PatchAttrs(String displayName, String externalId) {}

    record ParsedCreate(String displayName, String externalId, List<String> memberIds) {}
    record ParsedPatch(String displayName, String externalId, List<String> memberIds) {}
}
