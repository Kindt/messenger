package com.avandocmsg.messenger.common.i18n;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkerMessageSourcesTest {

    @Test
    void forWorker_resolvesCommonHealthKeysFromSharedBundle() {
        var messages = WorkerMessageSources.forWorker(
            WorkerMessageSourcesTest.class, "com.avandocmsg.messenger.i18n.messages_common");
        assertEquals(Locale.forLanguageTag("ru"), messages.locale());
        assertEquals("ok", messages.get("worker.health.ok"));
        assertEquals("не готов", messages.get("worker.health.not_ready"));
    }

    @Test
    void localeFromEnv_defaultsToRussian() {
        assertEquals(Locale.forLanguageTag("ru"), WorkerMessageSources.localeFromEnv());
    }
}
