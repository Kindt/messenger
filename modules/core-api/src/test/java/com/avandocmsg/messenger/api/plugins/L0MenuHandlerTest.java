package com.avandocmsg.messenger.api.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class L0MenuHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void rootMenuShowsWelcome() throws Exception {
        var config = MAPPER.readTree("""
            {
              "welcome_text": "HR FAQ",
              "menu": {
                "root": ["vacation"],
                "buttons": [
                  {"id": "vacation", "label": "Отпуск", "response_text": "Подача через портал", "url": "https://hr.example/leave"}
                ]
              }
            }
            """);
        var event = new com.avandocmsg.messenger.common.plugin.PluginEvent(
            "1", null, "L0", "mention", null, null, "hi", null, null);
        var response = L0MenuHandler.handle(event, config);
        assertTrue(response.messages().getFirst().text().contains("HR FAQ"));
        assertTrue(response.cards().getFirst().buttons().getFirst().label().contains("Отпуск"));
    }

    @Test
    void buttonShowsResponseAndUrl() throws Exception {
        var config = MAPPER.readTree("""
            {
              "menu": {
                "root": ["vacation"],
                "buttons": [
                  {"id": "vacation", "label": "Отпуск", "response_text": "Подача через портал", "url": "https://hr.example/leave"}
                ]
              }
            }
            """);
        var event = new com.avandocmsg.messenger.common.plugin.PluginEvent(
            "1", null, "L0", "button", null, null, null,
            java.util.Map.of("button_id", "vacation"), null);
        var response = L0MenuHandler.handle(event, config);
        var text = response.messages().getFirst().text();
        assertTrue(text.contains("Подача через портал"));
        assertTrue(text.contains("https://hr.example/leave"));
    }
}
