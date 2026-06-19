package com.avandocmsg.messenger.api.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelegramExportV1ParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parse_flatMessagesAndFormattedText() throws Exception {
        var root = MAPPER.readTree("""
            {
              "name": "Test chat",
              "messages": [
                {"id": 1, "type": "message", "text": "plain"},
                {"id": 2, "type": "service", "text": "joined"},
                {"id": 3, "type": "message", "text": ["bold ", {"type": "bold", "text": "x"}, "!"]}
              ]
            }
            """);
        var parsed = TelegramExportV1Parser.parse(root);
        assertEquals("Test chat", parsed.chatTitle());
        assertEquals(2, parsed.messages().size());
        assertEquals(1, parsed.messages().get(0).exportId());
        assertEquals("plain", parsed.messages().get(0).text());
        assertEquals("bold x!", parsed.messages().get(1).text());
    }

    @Test
    void parse_nestedExportJson() throws Exception {
        var root = MAPPER.readTree("""
            {
              "export_json": {
                "name": "Nested",
                "messages": [{"id": 10, "type": "message", "text": "hi"}]
              }
            }
            """);
        var parsed = TelegramExportV1Parser.parse(root);
        assertEquals("Nested", parsed.chatTitle());
        assertEquals(1, parsed.messages().size());
        assertEquals(10, parsed.messages().get(0).exportId());
    }

    @Test
    void parse_multilineConfigWithExportJson() throws Exception {
        var root = MAPPER.readTree("""
            {
              "export_json": {
                "name": "TG history",
                "messages": [
                  {"id": 1, "type": "message", "text": "one"},
                  {"id": 2, "type": "message", "text": "two"}
                ]
              }
            }
            """);
        var parsed = TelegramExportV1Parser.parse(root);
        assertEquals(2, parsed.messages().size());
    }

    @Test
    void parse_exportJsonAsString() throws Exception {
        var inner = """
            {"name":"TG history","messages":[{"id":1,"type":"message","text":"one"}]}
            """;
        var root = MAPPER.readTree("{\"export_json\":" + MAPPER.writeValueAsString(inner) + "}");
        var parsed = TelegramExportV1Parser.parse(root);
        assertEquals(1, parsed.messages().size());
    }

    @Test
    void parse_missingMessagesFails() throws Exception {
        var root = MAPPER.readTree("{\"name\":\"x\"}");
        assertThrows(IllegalArgumentException.class, () -> TelegramExportV1Parser.parse(root));
    }
}
