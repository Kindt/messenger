package com.avandocmsg.messenger.common.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Loads {@code .properties} as UTF-8 (standard {@link PropertyResourceBundle} uses ISO-8859-1 for byte streams).
 */
public final class Utf8Control extends ResourceBundle.Control {

    private static final String FORMAT_PROPERTIES = "properties";

    @Override
    public List<String> getFormats(String baseName) {
        return List.of(FORMAT_PROPERTIES);
    }

    @Override
    public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
        throws IllegalAccessException, InstantiationException, IOException {
        if (!FORMAT_PROPERTIES.equals(format)) {
            return null;
        }
        String bundleName = toBundleName(baseName, locale);
        String resourceName = toResourceName(bundleName, FORMAT_PROPERTIES);
        try (InputStream stream = loader.getResourceAsStream(resourceName)) {
            if (stream == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
    }
}
