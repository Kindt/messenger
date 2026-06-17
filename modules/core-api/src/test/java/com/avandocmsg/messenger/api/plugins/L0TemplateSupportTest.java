package com.avandocmsg.messenger.api.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class L0TemplateSupportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void rendersEventTextAndConfigVars() throws Exception {
        var config = MAPPER.readTree("""
            {"vars": {"it_phone": "1234"}}
            """);
        var event = new com.avandocmsg.messenger.common.plugin.PluginEvent(
            "1", null, "L0", "mention", UUID.randomUUID(), null, "hello", null, null);
        var out = L0TemplateSupport.render("ИТ: {{config.it_phone}} / {{event.text}}", event, config);
        assertEquals("ИТ: 1234 / hello", out);
    }

    @Test
    void missingKeyBecomesEmpty() throws Exception {
        var config = MAPPER.readTree("{}");
        var event = new com.avandocmsg.messenger.common.plugin.PluginEvent(
            "1", null, "L0", "mention", null, null, null, null, null);
        var out = L0TemplateSupport.render("x{{config.missing}}y", event, config);
        assertEquals("xy", out);
    }
}
