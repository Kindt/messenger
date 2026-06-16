package com.avandocmsg.messenger.api.plugins;

import com.avandocmsg.messenger.common.plugin.PluginButton;
import com.avandocmsg.messenger.common.plugin.PluginCard;
import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.avandocmsg.messenger.common.plugin.PluginResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * L0 config-only menu: buttons, static text, optional links (inline on server).
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
        var type = event.type() != null ? event.type() : "";
        if ("button".equals(type)) {
            var buttonId = stringPayload(event, "button_id");
            return buttonResponse(menu, buttonId);
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
        return rootCard(menu, config);
    }

    private static PluginResponse rootCard(JsonNode menu, JsonNode config) {
        var welcome = config.path("welcome_text").asText("Выберите раздел:");
        var buttons = buttonsForKeys(menu, menu.path("root"));
        return new PluginResponse(
            List.of(com.avandocmsg.messenger.common.plugin.PluginMessage.markdown(welcome)),
            List.of(new PluginCard("Меню", null, buttons)),
            null
        );
    }

    private static PluginResponse buttonResponse(JsonNode menu, String buttonId) {
        if (buttonId == null || buttonId.isBlank()) {
            return PluginResponse.text("Неизвестная кнопка.");
        }
        var items = menu.path("buttons");
        if (!items.isArray()) {
            return PluginResponse.text("Кнопка не найдена.");
        }
        for (var item : items) {
            if (buttonId.equals(item.path("id").asText())) {
                var text = item.path("response_text").asText("");
                var url = item.path("url").asText("");
                var sb = new StringBuilder(text);
                if (!url.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append("\n\n");
                    }
                    sb.append(url);
                }
                var childKeys = item.path("children");
                if (childKeys.isArray() && !childKeys.isEmpty()) {
                    var childButtons = buttonsForKeys(menu, childKeys);
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

    private static List<PluginButton> buttonsForKeys(JsonNode menu, JsonNode keys) {
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
                if (key.equals(item.path("id").asText())) {
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
