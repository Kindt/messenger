package com.avandocmsg.messenger.api.params;

/**
 * Invalid or missing UUID string from a path/query/body field; mapped to **400** + {@link com.avandocmsg.messenger.common.dto.ApiError}
 * via {@link com.avandocmsg.messenger.api.config.InvalidUuidParameterExceptionMapper}.
 */
public final class InvalidUuidParameterException extends RuntimeException {

    private final String paramKey;
    private final boolean missing;

    private InvalidUuidParameterException(String paramKey, boolean missing) {
        super(null, null, false, false);
        this.paramKey = paramKey;
        this.missing = missing;
    }

    public static InvalidUuidParameterException missing(String paramKey) {
        return new InvalidUuidParameterException(paramKey, true);
    }

    public static InvalidUuidParameterException invalidFormat(String paramKey) {
        return new InvalidUuidParameterException(paramKey, false);
    }

    /** Stable key for {@code param.{key}} in resource bundles (e.g. {@code chat_id}). */
    public String paramKey() {
        return paramKey;
    }

    public boolean missing() {
        return missing;
    }
}
