package com.avandocmsg.messenger.api.plugins;

import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Spec 014 L0+: simple declarative conditions on menu buttons.
 */
final class L0WhenSupport {

    private L0WhenSupport() {}

    static boolean matches(JsonNode when, PluginEvent event) {
        if (when == null || when.isMissingNode() || when.isNull()) {
            return true;
        }
        return matchesType(when, event) && matchesContains(when, event) && matchesEquals(when, event);
    }

    private static boolean matchesType(JsonNode when, PluginEvent event) {
        var expectedType = when.path("type").asText("");
        if (expectedType.isBlank()) {
            return true;
        }
        var actualType = event.type() != null ? event.type() : "";
        return expectedType.equals(actualType);
    }

    private static boolean matchesContains(JsonNode when, PluginEvent event) {
        var contains = when.path("text_contains").asText("");
        if (contains.isBlank()) {
            return true;
        }
        var text = event.text() != null ? event.text() : "";
        return text.toLowerCase().contains(contains.toLowerCase());
    }

    private static boolean matchesEquals(JsonNode when, PluginEvent event) {
        var equals = when.path("text_equals").asText("");
        if (equals.isBlank()) {
            return true;
        }
        var text = event.text() != null ? event.text().trim() : "";
        return text.equalsIgnoreCase(equals);
    }
}
