package com.avandocmsg.messenger.common.i18n;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.TreeSet;

/** Shared assertion helper for ru/en bundle parity tests. */
public final class BundleParityTestUtil {

    private BundleParityTestUtil() {
    }

    public static void assertSameKeys(ClassLoader loader, String resourceBasePath) throws Exception {
        var ruKeys = keys(loader, resourceBasePath + "_ru.properties");
        var enKeys = keys(loader, resourceBasePath + "_en.properties");
        if (!ruKeys.equals(enKeys)) {
            var onlyRu = new TreeSet<>(ruKeys);
            onlyRu.removeAll(enKeys);
            var onlyEn = new TreeSet<>(enKeys);
            onlyEn.removeAll(ruKeys);
            throw new AssertionError(
                resourceBasePath + " key mismatch; only RU: " + onlyRu + "; only EN: " + onlyEn);
        }
    }

    private static TreeSet<String> keys(ClassLoader loader, String resourcePath) throws Exception {
        try (InputStream is = loader.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Classpath resource not found: " + resourcePath);
            }
            var p = new Properties();
            p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            return new TreeSet<>(p.stringPropertyNames());
        }
    }
}
