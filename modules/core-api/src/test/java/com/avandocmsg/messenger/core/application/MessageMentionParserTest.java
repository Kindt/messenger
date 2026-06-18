package com.avandocmsg.messenger.core.application;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageMentionParserTest {

    @Test
    void parse_uuidAndAll() {
        var userId = UUID.randomUUID();
        var parsed = MessageMentionParser.parse("hi @" + userId + " and @all");
        assertTrue(parsed.mentionAll());
        assertEquals(1, parsed.userIds().size());
        assertEquals(userId, parsed.userIds().get(0));
    }

    @Test
    void parse_ignoresInvalidTokens() {
        var parsed = MessageMentionParser.parse("hello @not-a-uuid @ALL");
        assertTrue(parsed.mentionAll());
        assertTrue(parsed.userIds().isEmpty());
    }

    @Test
    void parse_emptyContent() {
        var parsed = MessageMentionParser.parse("   ");
        assertFalse(parsed.mentionAll());
        assertTrue(parsed.userIds().isEmpty());
    }
}
