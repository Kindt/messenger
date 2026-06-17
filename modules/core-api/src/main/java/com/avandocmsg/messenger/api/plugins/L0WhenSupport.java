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
        var expectedType = when.path("type").asText("");
        if (!expectedType.isBlank()) {
            var actualType = event.type() != null ? event.type() : "";
            if (!expectedType.equals(actualType)) {
                return false;
            }
        }
        var contains = when.path("text_contains").asText("");
        if (!contains.isBlank()) {
            var text = event.text() != null ? event.text() : "";
            if (!text.toLowerCase().contains(contains.toLowerCase())) {
                return false;
            }
        }
        var equals = when.path("text_equals").asText("");
        if (!equals.isBlank()) {
            var text = event.text() != null ? event.text().trim() : "";
            if (!text.equalsIgnoreCase(equals)) {
                return false;
            }
        }
        return true;
    }
}
