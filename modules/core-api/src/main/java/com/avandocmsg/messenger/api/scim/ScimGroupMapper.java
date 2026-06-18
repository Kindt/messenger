package com.avandocmsg.messenger.api.scim;

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
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> MEMBER_IDS = new TypeReference<>() {};

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
        var displayName = text(body, "displayName");
        var externalId = text(body, "externalId");
        var members = parseMemberIds(body.path("members"));
        return new ParsedCreate(displayName, externalId, members);
    }

    static ParsedPatch parsePatch(JsonNode body, List<String> existingMembers) {
        var displayName = text(body, "displayName");
        var externalId = text(body, "externalId");
        var members = new LinkedHashSet<>(existingMembers);

        if (body.has("members") && body.get("members").isArray()) {
            members.clear();
            members.addAll(parseMemberIds(body.get("members")));
        }

        if (body.has("Operations") && body.get("Operations").isArray()) {
            for (var op : body.get("Operations")) {
                var operation = op.path("op").asText("replace");
                var path = op.path("path").asText("");
                var value = op.get("value");
                if ("replace".equalsIgnoreCase(operation)) {
                    if (path.isBlank() && value != null && value.isObject()) {
                        displayName = firstNonBlank(text(value, "displayName"), displayName);
                        externalId = firstNonBlank(text(value, "externalId"), externalId);
                        if (value.has("members")) {
                            members.clear();
                            members.addAll(parseMemberIds(value.get("members")));
                        }
                    } else if ("displayName".equalsIgnoreCase(path) && value != null) {
                        displayName = value.asText();
                    } else if ("externalId".equalsIgnoreCase(path) && value != null) {
                        externalId = value.asText();
                    }
                } else if ("add".equalsIgnoreCase(operation) && path.startsWith("members")) {
                    if (value != null) {
                        if (value.isArray()) {
                            members.addAll(parseMemberIds(value));
                        } else if (value.isObject()) {
                            members.addAll(parseMemberIds(MAPPER.createArrayNode().add(value)));
                        }
                    }
                } else if ("remove".equalsIgnoreCase(operation) && path.startsWith("members")) {
                    var removed = extractMemberFilter(path);
                    if (removed != null) {
                        members.remove(removed);
                    }
                }
            }
        }
        return new ParsedPatch(displayName, externalId, List.copyOf(members));
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
            return null;
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
        var needle = "members[value eq \"";
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

    record ParsedCreate(String displayName, String externalId, List<String> memberIds) {}
    record ParsedPatch(String displayName, String externalId, List<String> memberIds) {}
}
