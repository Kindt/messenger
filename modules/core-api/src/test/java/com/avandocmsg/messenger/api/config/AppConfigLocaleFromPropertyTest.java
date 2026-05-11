package com.avandocmsg.messenger.api.config;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigLocaleFromPropertyTest {

    @Test
    void blank_defaultsToRussian() {
        assertEquals(Locale.forLanguageTag("ru"), AppConfig.localeFromProperty(null));
        assertEquals(Locale.forLanguageTag("ru"), AppConfig.localeFromProperty(""));
        assertEquals(Locale.forLanguageTag("ru"), AppConfig.localeFromProperty("   "));
    }

    @Test
    void englishUnderscore_normalizedToHyphen() {
        assertEquals(Locale.forLanguageTag("en-US"), AppConfig.localeFromProperty("en_US"));
    }

    @Test
    void simpleTags() {
        assertEquals(Locale.forLanguageTag("en"), AppConfig.localeFromProperty("en"));
        assertEquals(Locale.forLanguageTag("ru"), AppConfig.localeFromProperty("ru"));
    }
}
