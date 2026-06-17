package com.avandocmsg.messenger.api.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void templatesRenderInWelcomeAndButton() throws Exception {
        var config = MAPPER.readTree("""
            {
              "welcome_text": "Hello {{event.text}}",
              "vars": {"portal_url": "https://hr.example"},
              "menu": {
                "root": ["vacation"],
                "buttons": [
                  {
                    "id": "vacation",
                    "label": "Отпуск",
                    "response_text": "Portal: {{config.portal_url}}"
                  }
                ]
              }
            }
            """);
        var event = new com.avandocmsg.messenger.common.plugin.PluginEvent(
            "1", null, "L0", "mention", null, null, "Ivan", null, null);
        var root = L0MenuHandler.handle(event, config);
        assertEquals("Hello Ivan", root.messages().getFirst().text());

        var buttonEvent = new com.avandocmsg.messenger.common.plugin.PluginEvent(
            "2", null, "L0", "button", null, null, null,
            java.util.Map.of("button_id", "vacation"), null);
        var button = L0MenuHandler.handle(buttonEvent, config);
        assertEquals("Portal: https://hr.example", button.messages().getFirst().text());
    }

    @Test
    void slashCommandReturnsConfiguredResponse() throws Exception {
        var config = MAPPER.readTree("""
            {
              "vars": {"it_phone": "1234"},
              "slash_commands": [
                {"command": "/phone", "response_text": "IT: {{config.it_phone}}"}
              ],
              "menu": {"root": [], "buttons": []}
            }
            """);
        var event = new com.avandocmsg.messenger.common.plugin.PluginEvent(
            "1", null, "L0", "slash", null, null, "/phone", null, null);
        var response = L0MenuHandler.handle(event, config);
        assertEquals("IT: 1234", response.messages().getFirst().text());
    }

    @Test
    void whenFiltersRootButtons() throws Exception {
        var config = MAPPER.readTree("""
            {
              "menu": {
                "root": ["vacation", "it"],
                "buttons": [
                  {"id": "vacation", "label": "Отпуск", "when": {"text_contains": "отпуск"}},
                  {"id": "it", "label": "ИТ", "response_text": "help"}
                ]
              }
            }
            """);
        var noMatch = new com.avandocmsg.messenger.common.plugin.PluginEvent(
            "1", null, "L0", "mention", null, null, "hello", null, null);
        var rootNoVacation = L0MenuHandler.handle(noMatch, config);
        assertEquals(1, rootNoVacation.cards().getFirst().buttons().size());
        assertEquals("it", rootNoVacation.cards().getFirst().buttons().getFirst().id());

        var match = new com.avandocmsg.messenger.common.plugin.PluginEvent(
            "2", null, "L0", "mention", null, null, "про отпуск", null, null);
        var rootWithVacation = L0MenuHandler.handle(match, config);
        assertEquals(2, rootWithVacation.cards().getFirst().buttons().size());
    }

    @Test
    void buttonWhenMismatchReturnsUnavailable() throws Exception {
        var config = MAPPER.readTree("""
            {
              "menu": {
                "root": ["vacation"],
                "buttons": [
                  {"id": "vacation", "label": "Отпуск", "when": {"text_contains": "отпуск"}, "response_text": "ok"}
                ]
              }
            }
            """);
        var event = new com.avandocmsg.messenger.common.plugin.PluginEvent(
            "1", null, "L0", "button", null, null, "hi",
            java.util.Map.of("button_id", "vacation"), null);
        var response = L0MenuHandler.handle(event, config);
        assertTrue(response.messages().getFirst().text().contains("недоступна"));
    }
}
