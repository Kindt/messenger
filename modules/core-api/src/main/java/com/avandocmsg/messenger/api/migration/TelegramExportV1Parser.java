package com.avandocmsg.messenger.api.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** Parses Telegram Desktop export JSON (spec 022 US9, ADR telegram-export-import-v1). */
public final class TelegramExportV1Parser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record ParsedMessage(long exportId, String text) {}

    public record ParsedExport(String chatTitle, List<ParsedMessage> messages) {}

    private TelegramExportV1Parser() {}

    public static ParsedExport parse(JsonNode root) {
        if (root == null || root.isNull()) {
            throw new IllegalArgumentException("export_json missing");
        }
        var export = resolveExportNode(root);
        var title = export.path("name").asText("Telegram import");
        if (title.isBlank()) {
            title = "Telegram import";
        }
        var messagesNode = export.path("messages");
        if (!messagesNode.isArray()) {
            throw new IllegalArgumentException("messages array missing in export");
        }
        var messages = new ArrayList<ParsedMessage>();
        for (var node : messagesNode) {
            if (!"message".equals(node.path("type").asText())) {
                continue;
            }
            if (!node.has("id") || node.get("id").isNull()) {
                continue;
            }
            var exportId = node.get("id").asLong();
            var text = flattenText(node.get("text"));
            if (text.isBlank()) {
                continue;
            }
            messages.add(new ParsedMessage(exportId, text));
        }
        return new ParsedExport(title, messages);
    }

    private static JsonNode resolveExportNode(JsonNode root) {
        if (!root.has("export_json") || root.get("export_json").isNull()) {
            return root;
        }
        var exportField = root.get("export_json");
        if (exportField.isObject()) {
            return exportField;
        }
        if (exportField.isTextual()) {
            try {
                return MAPPER.readTree(exportField.asText());
            } catch (Exception e) {
                throw new IllegalArgumentException("export_json is not valid JSON");
            }
        }
        return root;
    }

    private static String flattenText(JsonNode textNode) {
        if (textNode == null || textNode.isNull()) {
            return "";
        }
        if (textNode.isTextual()) {
            return textNode.asText();
        }
        if (!textNode.isArray()) {
            return "";
        }
        var sb = new StringBuilder();
        for (var part : textNode) {
            if (part.isTextual()) {
                sb.append(part.asText());
            } else if (part.isObject() && part.has("text")) {
                sb.append(part.path("text").asText(""));
            }
        }
        return sb.toString();
    }
}
