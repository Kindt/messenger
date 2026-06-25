package com.avandocmsg.messenger.api.config;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonGZipWriterInterceptorTest {

    @Test
    void acceptsGzip_whenHeaderPresent() {
        var headers = mock(HttpHeaders.class);
        when(headers.getRequestHeaders()).thenReturn(new MultivaluedHashMap<>() {{
            add(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate");
        }});
        assertTrue(JsonGZipWriterInterceptor.acceptsGzip(headers));
    }

    @Test
    void acceptsGzip_falseWhenMissing() {
        assertFalse(JsonGZipWriterInterceptor.acceptsGzip(null));
    }

    @Test
    void aroundWriteTo_gzipWhenJsonPayloadLargeEnough() throws Exception {
        var interceptor = new JsonGZipWriterInterceptor();
        var requestHeaders = mock(HttpHeaders.class);
        when(requestHeaders.getRequestHeaders()).thenReturn(new MultivaluedHashMap<>() {{
            add(HttpHeaders.ACCEPT_ENCODING, "gzip");
        }});
        interceptor.setRequestHeaders(requestHeaders);

        var context = mock(WriterInterceptorContext.class);
        var responseHeaders = new MultivaluedHashMap<String, Object>();
        var sink = new ByteArrayOutputStream();
        var payload = "x".repeat(JsonGZipWriterInterceptor.MIN_BYTES);
        var outputRef = new java.util.concurrent.atomic.AtomicReference<OutputStream>(sink);

        when(context.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
        when(context.getHeaders()).thenReturn(responseHeaders);
        when(context.getOutputStream()).thenAnswer(invocation -> outputRef.get());
        doAnswer(invocation -> {
            outputRef.set(invocation.getArgument(0));
            return null;
        }).when(context).setOutputStream(org.mockito.ArgumentMatchers.any());
        doAnswer(invocation -> {
            outputRef.get().write(payload.getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(context).proceed();

        interceptor.aroundWriteTo(context);

        assertEquals("gzip", responseHeaders.getFirst(HttpHeaders.CONTENT_ENCODING));
        assertNull(responseHeaders.getFirst(HttpHeaders.CONTENT_LENGTH));
        try (var gzipIn = new GZIPInputStream(new java.io.ByteArrayInputStream(sink.toByteArray()))) {
            assertArrayEquals(payload.getBytes(StandardCharsets.UTF_8), gzipIn.readAllBytes());
        }
    }

    @Test
    void aroundWriteTo_skipsGzipWhenPayloadBelowThreshold() throws Exception {
        var interceptor = new JsonGZipWriterInterceptor();
        var requestHeaders = mock(HttpHeaders.class);
        when(requestHeaders.getRequestHeaders()).thenReturn(new MultivaluedHashMap<>() {{
            add(HttpHeaders.ACCEPT_ENCODING, "gzip");
        }});
        interceptor.setRequestHeaders(requestHeaders);

        var context = mock(WriterInterceptorContext.class);
        var responseHeaders = new MultivaluedHashMap<String, Object>();
        var sink = new ByteArrayOutputStream();
        var payload = "{\"ok\":true}";
        var outputRef = new java.util.concurrent.atomic.AtomicReference<OutputStream>(sink);

        when(context.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
        when(context.getHeaders()).thenReturn(responseHeaders);
        when(context.getOutputStream()).thenAnswer(invocation -> outputRef.get());
        doAnswer(invocation -> {
            outputRef.set(invocation.getArgument(0));
            return null;
        }).when(context).setOutputStream(org.mockito.ArgumentMatchers.any());
        doAnswer(invocation -> {
            outputRef.get().write(payload.getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(context).proceed();

        interceptor.aroundWriteTo(context);

        assertNull(responseHeaders.getFirst(HttpHeaders.CONTENT_ENCODING));
        assertArrayEquals(payload.getBytes(StandardCharsets.UTF_8), sink.toByteArray());
    }

    @Test
    void aroundWriteTo_skipsNonJson() throws IOException {
        var interceptor = new JsonGZipWriterInterceptor();
        var context = mock(WriterInterceptorContext.class);
        when(context.getMediaType()).thenReturn(MediaType.TEXT_PLAIN_TYPE);

        interceptor.aroundWriteTo(context);

        org.mockito.Mockito.verify(context).proceed();
    }
}
