package com.avandocmsg.messenger.core.adapter.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JdbcMessageWriteRepositoryTest {

    @Test
    void normalizeContent_stripsWhitespace() {
        assertEquals("hello", JdbcMessageWriteRepository.normalizeContent("  hello  "));
        assertNull(JdbcMessageWriteRepository.normalizeContent(null));
    }
}
