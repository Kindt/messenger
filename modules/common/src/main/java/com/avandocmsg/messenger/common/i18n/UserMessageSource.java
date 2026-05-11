package com.avandocmsg.messenger.common.i18n;

import java.util.Locale;

/** Resolved at server startup ({@code app.locale}); used for API {@link com.avandocmsg.messenger.common.dto.ApiError} bodies. */
public interface UserMessageSource {

    Locale locale();

    String get(String key);

    /** Formats using {@link java.text.MessageFormat} with {@link #locale()}. */
    String format(String key, Object... args);
}
