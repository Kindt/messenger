package com.avandocmsg.messenger.common.i18n;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Tries bundle base names in order (first wins); later bases typically hold shared defaults.
 */
public final class CompositeMessageSource implements UserMessageSource {

    private static final Utf8Control UTF8_CONTROL = new Utf8Control();

    private final Locale locale;
    private final List<ResourceBundle> bundles;

    public CompositeMessageSource(Locale locale, ClassLoader loader, List<String> bundleBaseNames) {
        this.locale = locale;
        List<ResourceBundle> list = new ArrayList<>();
        for (String base : bundleBaseNames) {
            try {
                list.add(ResourceBundle.getBundle(base, locale, loader, UTF8_CONTROL));
            } catch (MissingResourceException ignored) {
                // Locale-specific file may be absent; caller should ensure at least one bundle exists.
            }
        }
        this.bundles = List.copyOf(list);
    }

    @Override
    public Locale locale() {
        return locale;
    }

    @Override
    public String get(String key) {
        for (ResourceBundle rb : bundles) {
            try {
                return rb.getString(key);
            } catch (MissingResourceException ignored) {
                // try next bundle in chain
            }
        }
        return key;
    }

    @Override
    public String format(String key, Object... args) {
        String pattern = get(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, locale).format(args);
    }
}
