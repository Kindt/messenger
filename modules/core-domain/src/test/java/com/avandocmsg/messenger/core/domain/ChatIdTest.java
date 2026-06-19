package com.avandocmsg.messenger.core.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatIdTest {

    @Test
    void of_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new ChatId(null));
    }

    @Test
    void toString_returnsUuid() {
        var id = UUID.fromString("00000000-0000-4000-8000-000000000001");
        assertEquals(id.toString(), new ChatId(id).toString());
    }
}
