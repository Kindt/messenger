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

    private static final String KEY_SLASH_COMMANDS = "slash_commands";
    private static final String KEY_RESPONSE_TEXT = "response_text";
    private static final String KEY_CHILDREN = "children";
    private static final String KEY_TEXT_CONTAINS = "text_contains";
    private static final String KEY_TEXT_EQUALS = "text_equals";

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
            KEY_SLASH_COMMANDS,
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
        if (config.has(KEY_SLASH_COMMANDS) && !validateSlashCommands(config.get(KEY_SLASH_COMMANDS))) {
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
            if (!cmd.isObject() || !hasOnlyKeys(cmd, "command", KEY_RESPONSE_TEXT)) {
                return false;
            }
            if (!nonEmptyText(cmd, "command") || !cmd.has(KEY_RESPONSE_TEXT) || !cmd.get(KEY_RESPONSE_TEXT).isTextual()) {
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
            KEY_RESPONSE_TEXT,
            "url",
            KEY_CHILDREN,
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
        return validateChildren(button) && validateWhenField(button);
    }

    private static boolean validateChildren(JsonNode button) {
        if (!button.has(KEY_CHILDREN)) {
            return true;
        }
        var children = button.get(KEY_CHILDREN);
        if (!children.isArray()) {
            return false;
        }
        for (var child : children) {
            if (!child.isTextual() || child.asText().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateWhenField(JsonNode button) {
        return !button.has("when") || validateWhen(button.get("when"));
    }

    private static boolean validateWhen(JsonNode when) {
        if (!when.isObject() || !hasOnlyKeys(when, "type", KEY_TEXT_CONTAINS, KEY_TEXT_EQUALS)) {
            return false;
        }
        if (when.has("type") && !when.get("type").isTextual()) {
            return false;
        }
        if (when.has(KEY_TEXT_CONTAINS) && !when.get(KEY_TEXT_CONTAINS).isTextual()) {
            return false;
        }
        return !when.has(KEY_TEXT_EQUALS) || when.get(KEY_TEXT_EQUALS).isTextual();
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
