package com.avandocmsg.messenger.ws.i18n;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Same key set in {@code messages_ws_gateway_ru.properties} and {@code messages_ws_gateway_en.properties}.
 */
class MessagesWsGatewayBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_ws_gateway";

    @Test
    void russianAndEnglish_haveSameKeys() throws Exception {
        var ruKeys = keys(BASE + "_ru.properties");
        var enKeys = keys(BASE + "_en.properties");
        assertEquals(ruKeys, enKeys, () -> diffMessage(ruKeys, enKeys));
    }

    private static TreeSet<String> keys(String resourcePath) throws Exception {
        ClassLoader cl = MessagesWsGatewayBundleParityTest.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Classpath resource not found: " + resourcePath);
            }
            var p = new Properties();
            p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            return new TreeSet<>(p.stringPropertyNames());
        }
    }

    private static String diffMessage(TreeSet<String> a, TreeSet<String> b) {
        var onlyRu = new TreeSet<>(a);
        onlyRu.removeAll(b);
        var onlyEn = new TreeSet<>(b);
        onlyEn.removeAll(a);
        return "only in RU: " + onlyRu + "; only in EN: " + onlyEn;
    }
}
