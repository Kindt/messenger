package com.avandocmsg.messenger.api.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class L0MenuConfigValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void acceptsMinimalV1Menu() {
        var config = MAPPER.createObjectNode();
        var menu = config.putObject("menu");
        menu.putArray("root").add("vacation");
        var buttons = menu.putArray("buttons");
        buttons.addObject().put("id", "vacation").put("label", "Vacation").put("response_text", "Portal");
        assertTrue(L0MenuConfigValidator.validate(config).isEmpty());
    }

    @Test
    void acceptsV2WithSlashAndWhen() throws Exception {
        var config = MAPPER.readTree(
            """
            {
              "config_schema_version": 2,
              "vars": { "phone": "1234" },
              "slash_commands": [{ "command": "/phone", "response_text": "IT: {{config.phone}}" }],
              "menu": {
                "root": ["vacation"],
                "buttons": [{
                  "id": "vacation",
                  "label": "Leave",
                  "when": { "text_contains": "leave" },
                  "response_text": "Form"
                }]
              }
            }
            """
        );
        assertTrue(L0MenuConfigValidator.validate(config).isEmpty());
    }

    @Test
    void rejectsMissingMenu() {
        assertFalse(L0MenuConfigValidator.validate(MAPPER.createObjectNode()).isEmpty());
    }

    @Test
    void rejectsUnknownRootButton() {
        var config = MAPPER.createObjectNode();
        var menu = config.putObject("menu");
        menu.putArray("root").add("missing");
        menu.putArray("buttons").addObject().put("id", "other").put("label", "X");
        assertFalse(L0MenuConfigValidator.validate(config).isEmpty());
    }

    @Test
    void rejectsDuplicateButtonIds() {
        var config = MAPPER.createObjectNode();
        var menu = config.putObject("menu");
        menu.putArray("root").add("a");
        var buttons = menu.putArray("buttons");
        buttons.addObject().put("id", "a").put("label", "A");
        buttons.addObject().put("id", "a").put("label", "B");
        assertFalse(L0MenuConfigValidator.validate(config).isEmpty());
    }
}
