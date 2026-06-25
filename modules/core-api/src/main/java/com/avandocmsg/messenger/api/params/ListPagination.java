package com.avandocmsg.messenger.api.params;

import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import jakarta.ws.rs.core.Response;

import java.util.Optional;

/** FR-077 list pagination limits (spec 025). */
public final class ListPagination {

    public static final int MAX_LIMIT = 1000;
    public static final int DEFAULT_CHAT_LIST_LIMIT = 100;
    public static final int DEFAULT_MESSAGE_LIST_LIMIT = 50;

    private ListPagination() {
    }

    public record Page(int offset, int limit) {
    }

    public enum Validation {
        OK,
        LIMIT_TOO_LOW,
        LIMIT_TOO_HIGH,
        OFFSET_NEGATIVE
    }

    public static Validation validate(Integer limit, Integer offset) {
        if (offset != null && offset < 0) {
            return Validation.OFFSET_NEGATIVE;
        }
        if (limit != null && limit < 1) {
            return Validation.LIMIT_TOO_LOW;
        }
        if (limit != null && limit > MAX_LIMIT) {
            return Validation.LIMIT_TOO_HIGH;
        }
        return Validation.OK;
    }

    public static Page resolve(Integer limit, Integer offset, int defaultLimit) {
        var lim = limit != null ? limit : defaultLimit;
        var off = offset != null ? Math.max(0, offset) : 0;
        return new Page(off, lim);
    }

    public static <T> java.util.List<T> slice(java.util.List<T> items, Page page) {
        if (items == null || items.isEmpty() || page.offset() >= items.size()) {
            return java.util.List.of();
        }
        var end = Math.min(page.offset() + page.limit(), items.size());
        return items.subList(page.offset(), end);
    }

    public static Optional<Response> badRequest(Validation validation, UserMessageSource messages) {
        if (validation == Validation.OK) {
            return Optional.empty();
        }
        var body = switch (validation) {
            case LIMIT_TOO_LOW -> new ApiError(400, messages.get("error.list.limit_too_low"));
            case LIMIT_TOO_HIGH -> new ApiError(400, messages.format("error.list.limit_too_high",
                String.valueOf(MAX_LIMIT)));
            case OFFSET_NEGATIVE -> new ApiError(400, messages.get("error.list.offset_negative"));
            case OK -> throw new IllegalStateException("OK");
        };
        return Optional.of(Response.status(Response.Status.BAD_REQUEST).entity(body).build());
    }
}
