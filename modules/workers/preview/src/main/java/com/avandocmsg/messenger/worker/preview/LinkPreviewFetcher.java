package com.avandocmsg.messenger.worker.preview;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** SSRF-aware HTTP GET + minimal HTML title / og:title extraction. */
final class LinkPreviewFetcher {
    private static final Logger log = LoggerFactory.getLogger(LinkPreviewFetcher.class);

    private final OkHttpClient http;
    private final int maxBodyBytes;

    LinkPreviewFetcher(Duration connectTimeout, Duration readTimeout, int maxBodyBytes) {
        this.maxBodyBytes = Math.max(1024, maxBodyBytes);
        this.http = new OkHttpClient.Builder()
            .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .callTimeout(readTimeout.toMillis() + connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .build();
    }

    Optional<String> fetchPreviewTitle(String rawUrl) {
        URI uri;
        try {
            uri = SsrfGuard.parseHttpUri(rawUrl);
            SsrfGuard.validateHostAllowed(uri.getHost());
        } catch (IOException e) {
            log.debug("Preview URL rejected: {}", e.getMessage());
            return Optional.empty();
        }
        String canonical = uri.toString();
        Request request = new Request.Builder().url(canonical).get().build();
        try (var response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.debug("Preview HTTP status {} for {}", response.code(), canonical);
                return Optional.empty();
            }
            try (var peeked = response.peekBody(maxBodyBytes)) {
                byte[] buf = peeked.bytes();
                var html = new String(buf, StandardCharsets.UTF_8);
                return Optional.ofNullable(extractTitle(html));
            }
        } catch (IOException e) {
            log.debug("Preview fetch failed for {}", canonical, e);
            return Optional.empty();
        }
    }

    private static String extractTitle(String html) {
        Document doc = Jsoup.parse(html);
        var og = doc.selectFirst("meta[property=og:title]");
        if (og != null) {
            var c = og.attr("content");
            if (c != null && !c.isBlank()) {
                return c.trim();
            }
        }
        var title = doc.title();
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return null;
    }
}
