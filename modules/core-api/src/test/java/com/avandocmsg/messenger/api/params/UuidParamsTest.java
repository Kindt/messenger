package com.avandocmsg.messenger.api.params;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UuidParamsTest {

    @Test
    void required_parsesTrimmedUuid() {
        var id = UUID.randomUUID();
        assertEquals(id, UuidParams.required("  " + id + "  ", "chat_id"));
    }

    @Test
    void required_missingThrows() {
        assertThrows(InvalidUuidParameterException.class,
            () -> UuidParams.required(null, "chat_id"));
        assertThrows(InvalidUuidParameterException.class,
            () -> UuidParams.required("", "chat_id"));
        assertThrows(InvalidUuidParameterException.class,
            () -> UuidParams.required("   ", "chat_id"));
    }

    @Test
    void required_invalidFormatThrows() {
        var ex = assertThrows(InvalidUuidParameterException.class,
            () -> UuidParams.required("not-a-uuid", "member_id"));
        assertEquals("member_id", ex.paramKey());
    }
}
