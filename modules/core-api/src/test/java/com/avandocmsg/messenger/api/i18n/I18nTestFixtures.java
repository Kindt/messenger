package com.avandocmsg.messenger.api.i18n;

import com.avandocmsg.messenger.common.i18n.CompositeMessageSource;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import java.util.List;
import java.util.Locale;

public final class I18nTestFixtures {

    private I18nTestFixtures() {
    }

    /** English bundles for assertions stable across default server locale (Russian). */
    public static UserMessageSource messagesEn() {
        return new CompositeMessageSource(Locale.ENGLISH, I18nTestFixtures.class.getClassLoader(),
            List.of(
                "com.avandocmsg.messenger.i18n.messages_core_api",
                "com.avandocmsg.messenger.i18n.messages_common"));
    }
}
