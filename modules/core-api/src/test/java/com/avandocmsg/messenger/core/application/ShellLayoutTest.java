package com.avandocmsg.messenger.core.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShellLayoutTest {

    @Test
    void authLayout_mapsAuthSplitOnly() {
        assertEquals("auth-split", ShellLayout.authLayout("auth-split"));
        assertEquals("default", ShellLayout.authLayout("compact"));
        assertEquals("default", ShellLayout.authLayout("default"));
    }

    @Test
    void postLoginLayout_mapsCompactAndAuthSplit() {
        assertEquals("default", ShellLayout.postLoginLayout("auth-split"));
        assertEquals("compact", ShellLayout.postLoginLayout("compact"));
        assertEquals("default", ShellLayout.postLoginLayout("default"));
    }

    @Test
    void validateRequired_rejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> ShellLayout.validateRequired("portal"));
    }
}
