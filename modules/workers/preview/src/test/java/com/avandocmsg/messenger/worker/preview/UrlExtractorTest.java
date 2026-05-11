package com.avandocmsg.messenger.worker.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlExtractorTest {

    @Test
    void firstHttpUrl_findsHttps() {
        assertEquals("https://example.com/path",
            UrlExtractor.firstHttpUrl("see https://example.com/path here").orElseThrow());
    }

    @Test
    void firstHttpUrl_trimsTrailingParen() {
        assertEquals("https://a.test/x",
            UrlExtractor.firstHttpUrl("(link https://a.test/x)").orElseThrow());
    }

    @Test
    void firstHttpUrl_emptyWhenNone() {
        assertTrue(UrlExtractor.firstHttpUrl("no url").isEmpty());
    }
}
