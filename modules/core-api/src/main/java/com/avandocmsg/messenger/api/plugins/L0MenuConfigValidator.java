package com.avandocmsg.messenger.api.plugins;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/**
 * Structural validation for L0 menu config (schema v1/v2). No external JSON Schema engine.
 */
public final class L0MenuConfigValidator {

    public static final String ERROR_KEY = "error.plugin.l0_config_invalid";

    private L0MenuConfigValidator() {}

    public static Optional<String> validate(JsonNode config) {
        if (config == null || !config.isObject()) {
            return Optional.of(ERROR_KEY);
        }
        if (!hasOnlyKeys(
            config,
            "config_schema_version",
            "welcome_text",
            "vars",
            "slash_commands",
            "menu"
        )) {
            return Optional.of(ERROR_KEY);
        }
        var version = config.get("config_schema_version");
        if (version != null && (!version.isInt() || version.intValue() < 1 || version.intValue() > 2)) {
            return Optional.of(ERROR_KEY);
        }
        if (config.has("vars") && !validateVars(config.get("vars"))) {
            return Optional.of(ERROR_KEY);
        }
        if (config.has("slash_commands") && !validateSlashCommands(config.get("slash_commands"))) {
            return Optional.of(ERROR_KEY);
        }
        if (!validateMenu(config.get("menu"))) {
            return Optional.of(ERROR_KEY);
        }
        return Optional.empty();
    }

    private static boolean validateVars(JsonNode vars) {
        if (!vars.isObject()) {
            return false;
        }
        Iterator<String> names = vars.fieldNames();
        while (names.hasNext()) {
            var value = vars.get(names.next());
            if (value == null || !value.isTextual()) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateSlashCommands(JsonNode commands) {
        if (!commands.isArray()) {
            return false;
        }
        for (var cmd : commands) {
            if (!cmd.isObject() || !hasOnlyKeys(cmd, "command", "response_text")) {
                return false;
            }
            if (!nonEmptyText(cmd, "command") || !cmd.has("response_text") || !cmd.get("response_text").isTextual()) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateMenu(JsonNode menu) {
        if (menu == null || !menu.isObject() || !hasOnlyKeys(menu, "root", "buttons")) {
            return false;
        }
        var root = menu.get("root");
        var buttons = menu.get("buttons");
        if (root == null || !root.isArray() || buttons == null || !buttons.isArray() || buttons.isEmpty()) {
            return false;
        }
        Set<String> buttonIds = new HashSet<>();
        for (var button : buttons) {
            if (!validateButton(button, buttonIds)) {
                return false;
            }
        }
        for (var key : root) {
            if (!key.isTextual() || key.asText().isBlank() || !buttonIds.contains(key.asText())) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateButton(JsonNode button, Set<String> buttonIds) {
        if (!button.isObject()) {
            return false;
        }
        if (!hasOnlyKeys(
            button,
            "id",
            "label",
            "response_text",
            "url",
            "children",
            "when"
        )) {
            return false;
        }
        if (!nonEmptyText(button, "id") || !nonEmptyText(button, "label")) {
            return false;
        }
        var id = button.get("id").asText();
        if (!buttonIds.add(id)) {
            return false;
        }
        if (button.has("children")) {
            var children = button.get("children");
            if (!children.isArray()) {
                return false;
            }
            for (var child : children) {
                if (!child.isTextual() || child.asText().isBlank()) {
                    return false;
                }
            }
        }
        if (button.has("when") && !validateWhen(button.get("when"))) {
            return false;
        }
        return true;
    }

    private static boolean validateWhen(JsonNode when) {
        if (!when.isObject() || !hasOnlyKeys(when, "type", "text_contains", "text_equals")) {
            return false;
        }
        if (when.has("type") && !when.get("type").isTextual()) {
            return false;
        }
        if (when.has("text_contains") && !when.get("text_contains").isTextual()) {
            return false;
        }
        if (when.has("text_equals") && !when.get("text_equals").isTextual()) {
            return false;
        }
        return true;
    }

    private static boolean nonEmptyText(JsonNode node, String field) {
        var value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank();
    }

    private static boolean hasOnlyKeys(JsonNode node, String... allowed) {
        Set<String> allowedSet = Set.of(allowed);
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            if (!allowedSet.contains(names.next())) {
                return false;
            }
        }
        return true;
    }
}
