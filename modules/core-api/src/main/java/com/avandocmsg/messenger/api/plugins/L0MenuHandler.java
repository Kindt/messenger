package com.avandocmsg.messenger.api.plugins;

import com.avandocmsg.messenger.common.plugin.PluginButton;
import com.avandocmsg.messenger.common.plugin.PluginCard;
import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.avandocmsg.messenger.common.plugin.PluginResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * L0 config-only menu: buttons, static text, optional links, templates, slash commands (spec 014 L0+).
 */
public final class L0MenuHandler {

    private L0MenuHandler() {}

    public static PluginResponse handle(PluginEvent event, JsonNode config) {
        if (config == null || config.isMissingNode()) {
            return PluginResponse.text("Меню не настроено.");
        }
        var menu = config.path("menu");
        if (menu.isMissingNode()) {
            return PluginResponse.text("Меню не настроено.");
        }
        var slash = trySlashCommand(event, config);
        if (slash != null) {
            return slash;
        }
        var type = event.type() != null ? event.type() : "";
        if ("button".equals(type)) {
            var buttonId = stringPayload(event, "button_id");
            return buttonResponse(event, menu, config, buttonId);
        }
        if ("slash".equals(type) && event.text() != null && event.text().startsWith("/echo ")) {
            return PluginResponse.text(event.text().substring(6).trim());
        }
        if ("mention".equals(type) || "slash".equals(type)) {
            var text = event.text() != null ? event.text().trim().toLowerCase() : "";
            if (text.equals("ping") || text.equals("@ping")) {
                return PluginResponse.text("pong (L0)");
            }
        }
        return rootCard(event, menu, config);
    }

    private static PluginResponse trySlashCommand(PluginEvent event, JsonNode config) {
        if (!"slash".equals(event.type()) || event.text() == null) {
            return null;
        }
        var commands = config.path("slash_commands");
        if (!commands.isArray()) {
            return null;
        }
        var text = event.text().trim();
        for (var cmd : commands) {
            if (text.equals(cmd.path("command").asText())) {
                var responseText = L0TemplateSupport.render(cmd.path("response_text").asText(""), event, config);
                return PluginResponse.text(responseText.isBlank() ? cmd.path("command").asText() : responseText);
            }
        }
        return null;
    }

    private static PluginResponse rootCard(PluginEvent event, JsonNode menu, JsonNode config) {
        var welcome = L0TemplateSupport.render(config.path("welcome_text").asText("Выберите раздел:"), event, config);
        var buttons = buttonsForKeys(event, menu, config, menu.path("root"));
        return new PluginResponse(
            List.of(com.avandocmsg.messenger.common.plugin.PluginMessage.markdown(welcome)),
            List.of(new PluginCard("Меню", null, buttons)),
            null
        );
    }

    private static PluginResponse buttonResponse(PluginEvent event, JsonNode menu, JsonNode config, String buttonId) {
        if (buttonId == null || buttonId.isBlank()) {
            return PluginResponse.text("Неизвестная кнопка.");
        }
        var items = menu.path("buttons");
        if (!items.isArray()) {
            return PluginResponse.text("Кнопка не найдена.");
        }
        for (var item : items) {
            if (buttonId.equals(item.path("id").asText())) {
                if (!L0WhenSupport.matches(item.path("when"), event)) {
                    return PluginResponse.text("Кнопка недоступна.");
                }
                var text = L0TemplateSupport.render(item.path("response_text").asText(""), event, config);
                var url = L0TemplateSupport.render(item.path("url").asText(""), event, config);
                var sb = new StringBuilder(text);
                if (!url.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append("\n\n");
                    }
                    sb.append(url);
                }
                var childKeys = item.path("children");
                if (childKeys.isArray() && !childKeys.isEmpty()) {
                    var childButtons = buttonsForKeys(event, menu, config, childKeys);
                    return new PluginResponse(
                        List.of(com.avandocmsg.messenger.common.plugin.PluginMessage.markdown(sb.toString())),
                        List.of(new PluginCard(item.path("label").asText("Далее"), null, childButtons)),
                        null
                    );
                }
                return PluginResponse.text(sb.isEmpty() ? item.path("label").asText() : sb.toString());
            }
        }
        return PluginResponse.text("Кнопка не найдена: " + buttonId);
    }

    private static List<PluginButton> buttonsForKeys(
        PluginEvent event,
        JsonNode menu,
        JsonNode config,
        JsonNode keys
    ) {
        var out = new ArrayList<PluginButton>();
        if (!keys.isArray()) {
            return out;
        }
        var items = menu.path("buttons");
        if (!items.isArray()) {
            return out;
        }
        for (var keyNode : keys) {
            var key = keyNode.asText();
            for (var item : items) {
                if (key.equals(item.path("id").asText()) && L0WhenSupport.matches(item.path("when"), event)) {
                    out.add(new PluginButton(key, item.path("label").asText(key)));
                    break;
                }
            }
        }
        return out;
    }

    private static String stringPayload(PluginEvent event, String key) {
        Map<String, Object> payload = event.payload();
        if (payload == null) {
            return null;
        }
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }
}
