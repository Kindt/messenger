package com.avandocmsg.messenger.api.config;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/** FR-080: gzip JSON responses larger than {@link #MIN_BYTES} when client accepts gzip. */
@Provider
public class JsonGZipWriterInterceptor implements WriterInterceptor {

    static final int MIN_BYTES = 1024;

    private HttpHeaders requestHeaders;

    @jakarta.ws.rs.core.Context
    public void setRequestHeaders(HttpHeaders requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
        var mediaType = context.getMediaType();
        if (mediaType == null || !MediaType.APPLICATION_JSON_TYPE.isCompatible(mediaType)) {
            context.proceed();
            return;
        }
        if (!acceptsGzip(requestHeaders)) {
            context.proceed();
            return;
        }

        var original = context.getOutputStream();
        var buffer = new ByteArrayOutputStream();
        context.setOutputStream(buffer);
        context.proceed();

        var plain = buffer.toByteArray();
        if (plain.length < MIN_BYTES) {
            original.write(plain);
            return;
        }

        context.getHeaders().putSingle(HttpHeaders.CONTENT_ENCODING, "gzip");
        context.getHeaders().add(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
        context.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
        try (var gzip = new GZIPOutputStream(original)) {
            gzip.write(plain);
        }
    }

    static boolean acceptsGzip(HttpHeaders headers) {
        if (headers == null) {
            return false;
        }
        var acceptEncoding = headers.getRequestHeaders().getFirst(HttpHeaders.ACCEPT_ENCODING);
        return acceptEncoding != null && acceptEncoding.toLowerCase().contains("gzip");
    }
}
