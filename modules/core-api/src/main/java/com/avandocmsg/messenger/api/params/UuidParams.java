package com.avandocmsg.messenger.api.params;

import java.util.UUID;

/** Shared UUID parsing for REST paths/query; failures become HTTP 400 + {@link com.avandocmsg.messenger.common.dto.ApiError} via {@link com.avandocmsg.messenger.api.config.InvalidUuidParameterExceptionMapper}. */
public final class UuidParams {

    private UuidParams() {
    }

    /**
     * @param raw      raw string (path segment, query, or body field)
     * @param paramKey stable key for bundles ({@code param.{paramKey}}), e.g. {@code "chat_id"}
     */
    public static UUID required(String raw, String paramKey) {
        if (raw == null || raw.isBlank()) {
            throw InvalidUuidParameterException.missing(paramKey);
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw InvalidUuidParameterException.invalidFormat(paramKey);
        }
    }
}
