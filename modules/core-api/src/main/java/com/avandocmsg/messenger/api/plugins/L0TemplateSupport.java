package com.avandocmsg.messenger.api.plugins;

import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spec 014 L0+: whitelist template placeholders in menu text (no eval).
 */
final class L0TemplateSupport {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z0-9_.]+)}}");

    private L0TemplateSupport() {}

    static String render(String template, PluginEvent event, JsonNode config) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        var sb = new StringBuilder();
        while (matcher.find()) {
            var key = matcher.group(1);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(resolve(key, event, config)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resolve(String key, PluginEvent event, JsonNode config) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return switch (key) {
            case "event.text" -> event.text() != null ? event.text() : "";
            case "user_id" -> event.userId() != null ? event.userId().toString() : "";
            case "chat_id" -> event.chatId() != null ? event.chatId().toString() : "";
            default -> {
                if (key.startsWith("config.")) {
                    var configKey = key.substring("config.".length());
                    var fromVars = configText(config.path("vars"), configKey);
                    if (!fromVars.isEmpty()) {
                        yield fromVars;
                    }
                    yield configText(config, configKey);
                }
                yield configText(config.path("vars"), key);
            }
        };
    }

    private static String configText(JsonNode node, String key) {
        if (node == null || node.isMissingNode() || key == null || key.isBlank()) {
            return "";
        }
        var value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        if (value.isValueNode()) {
            return value.asText("");
        }
        return value.toString();
    }
}
