package com.avandocmsg.messenger.core.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAvatarRendererTest {

    @Test
    void initialsFromDisplayName_twoWords() {
        assertEquals("JD", DefaultAvatarRenderer.initialsFor("John Doe", "jdoe"));
    }

    @Test
    void initialsFromUsername_whenDisplayNameBlank() {
        assertEquals("AL", DefaultAvatarRenderer.initialsFor("  ", "alice"));
    }

    @Test
    void pngBytes_producesPngHeader() {
        var bytes = DefaultAvatarRenderer.pngBytes("Test User", "test");
        assertTrue(bytes.length > 8);
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals('P', bytes[1]);
    }
}
