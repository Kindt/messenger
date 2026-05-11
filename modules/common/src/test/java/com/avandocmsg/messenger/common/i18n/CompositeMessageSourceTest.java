package com.avandocmsg.messenger.common.i18n;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeMessageSourceTest {

    private static final String COMMON_BASE = "com.avandocmsg.messenger.i18n.messages_common";

    @Test
    void englishBundle_internalError() {
        var src = new CompositeMessageSource(Locale.ENGLISH,
            CompositeMessageSourceTest.class.getClassLoader(),
            List.of(COMMON_BASE));
        assertEquals("Internal server error", src.get("error.internal"));
        assertEquals(Locale.ENGLISH, src.locale());
    }

    @Test
    void russianBundle_internalError_utf8() {
        var src = new CompositeMessageSource(Locale.forLanguageTag("ru"),
            CompositeMessageSourceTest.class.getClassLoader(),
            List.of(COMMON_BASE));
        assertEquals("Внутренняя ошибка сервера", src.get("error.internal"));
    }

    @Test
    void format_insufficientRole_russian() {
        var src = new CompositeMessageSource(Locale.forLanguageTag("ru"),
            CompositeMessageSourceTest.class.getClassLoader(),
            List.of(COMMON_BASE));
        assertEquals("Недостаточно прав", src.get("error.insufficient_role"));
    }

    @Test
    void missingKey_returnsKey() {
        var src = new CompositeMessageSource(Locale.ENGLISH,
            CompositeMessageSourceTest.class.getClassLoader(),
            List.of(COMMON_BASE));
        assertEquals("totally.missing.key", src.get("totally.missing.key"));
    }

    @Test
    void chain_firstBundleWinsWhenKeyInBoth() {
        var a = "com.avandocmsg.messenger.i18n.messages_chain_a";
        var b = "com.avandocmsg.messenger.i18n.messages_chain_b";
        var src = new CompositeMessageSource(Locale.ENGLISH,
            CompositeMessageSourceTest.class.getClassLoader(),
            List.of(a, b));
        assertEquals("from A", src.get("chain.override"));
        assertEquals("only B", src.get("chain.only.b"));
        assertEquals("only A", src.get("chain.only.a"));
    }
}
