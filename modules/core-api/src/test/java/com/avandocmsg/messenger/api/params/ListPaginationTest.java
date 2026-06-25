package com.avandocmsg.messenger.api.params;

import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListPaginationTest {

    @Test
    void resolve_usesDefaultsWhenParamsMissing() {
        var page = ListPagination.resolve(null, null, ListPagination.DEFAULT_CHAT_LIST_LIMIT);
        assertEquals(0, page.offset());
        assertEquals(ListPagination.DEFAULT_CHAT_LIST_LIMIT, page.limit());
    }

    @Test
    void validate_rejectsLimitAboveMax() {
        assertEquals(ListPagination.Validation.LIMIT_TOO_HIGH,
            ListPagination.validate(ListPagination.MAX_LIMIT + 1, 0));
    }

    @Test
    void validate_rejectsNegativeOffset() {
        assertEquals(ListPagination.Validation.OFFSET_NEGATIVE, ListPagination.validate(50, -1));
    }

    @Test
    void badRequest_limitTooHigh_is400WithMessage() {
        var response = ListPagination.badRequest(ListPagination.Validation.LIMIT_TOO_HIGH,
            I18nTestFixtures.messagesEn()).orElseThrow();
        assertEquals(400, response.getStatus());
        var error = (com.avandocmsg.messenger.common.dto.ApiError) response.getEntity();
        assertTrue(error.message().contains("1000"));
    }

    @Test
    void slice_returnsWindow() {
        var items = List.of("a", "b", "c", "d");
        var page = new ListPagination.Page(1, 2);
        assertEquals(List.of("b", "c"), ListPagination.slice(items, page));
    }
}
