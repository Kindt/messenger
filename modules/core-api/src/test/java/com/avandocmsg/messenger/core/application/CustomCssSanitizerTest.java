package com.avandocmsg.messenger.core.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomCssSanitizerTest {
    private final CustomCssSanitizer sanitizer = new CustomCssSanitizer();

    @Test
    void stripsDangerousConstructs() {
        var css = "@import url('https://evil'); .x { background:url(javascript:alert(1)); width: expression(alert(1)); }";
        var sanitized = sanitizer.sanitize(css);
        assertFalse(sanitized.contains("@import"));
        assertFalse(sanitized.toLowerCase().contains("javascript:"));
        assertFalse(sanitized.toLowerCase().contains("expression("));
    }

    @Test
    void rejectsCssOver32kb() {
        var big = "a".repeat(33 * 1024);
        assertThrows(IllegalArgumentException.class, () -> sanitizer.sanitize(big));
    }

    @Test
    void keepsSafeCss() {
        var sanitized = sanitizer.sanitize(".banner { color: #123456; }");
        assertTrue(sanitized.contains("banner"));
    }
}
