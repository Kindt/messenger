package com.avandocmsg.messenger.common.i18n;

import java.util.List;
import java.util.Locale;

/** Factory for worker {@link UserMessageSource} chains (module bundle + {@code messages_common}). */
public final class WorkerMessageSources {

    private WorkerMessageSources() {
    }

    public static UserMessageSource forWorker(Class<?> anchorClass, String workerBundleBase) {
        var locale = localeFromEnv();
        return new CompositeMessageSource(
            locale,
            anchorClass.getClassLoader(),
            List.of(workerBundleBase, "com.avandocmsg.messenger.i18n.messages_common"));
    }

    public static Locale localeFromEnv() {
        var raw = System.getenv("APP_LOCALE");
        if (raw == null || raw.isBlank()) {
            return Locale.forLanguageTag("ru");
        }
        return Locale.forLanguageTag(raw.trim().replace('_', '-'));
    }
}
