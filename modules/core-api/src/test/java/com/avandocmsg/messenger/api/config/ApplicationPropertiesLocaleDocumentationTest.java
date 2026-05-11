package com.avandocmsg.messenger.api.config;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardrail: {@code application.properties} keeps an inline comment above {@code app.locale}
 * mentioning {@code APP_LOCALE} (see {@link AppConfigLocaleFromPropertyTest}).
 */
class ApplicationPropertiesLocaleDocumentationTest {

    @Test
    void appLocale_precedingCommentDocumentsEnvOverride() throws Exception {
        List<String> lines = loadLines();
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("app.locale=")) {
                idx = i;
                break;
            }
        }
        assertTrue(idx >= 0, "application.properties must define app.locale=");

        var comment = new StringBuilder();
        for (int i = idx - 1; i >= 0; i--) {
            String t = lines.get(i).trim();
            if (!t.startsWith("#")) {
                break;
            }
            comment.append(t, 1, t.length()).append(' ');
        }
        String block = comment.toString();
        assertFalse(block.isBlank(), "app.locale= must be preceded by # comment lines");
        assertTrue(block.contains("APP_LOCALE"), "comment above app.locale must mention APP_LOCALE");
        assertTrue(block.contains("Локаль"), "comment above app.locale must mention Локаль");
    }

    private static List<String> loadLines() throws Exception {
        ClassLoader cl = ApplicationPropertiesLocaleDocumentationTest.class.getClassLoader();
        try (var is = cl.getResourceAsStream("application.properties")) {
            assertNotNull(is, "classpath application.properties missing");
            var out = new ArrayList<String>();
            try (var br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.add(line);
                }
            }
            return out;
        }
    }
}
