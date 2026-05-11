package com.avandocmsg.messenger.worker.preview;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UrlExtractor {
    private static final Pattern FIRST_HTTP_URL = Pattern.compile(
        "(https?://[^\\s<>\"{}|\\\\^`\\[\\]]+)",
        Pattern.CASE_INSENSITIVE
    );

    private UrlExtractor() {
    }

    static Optional<String> firstHttpUrl(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher m = FIRST_HTTP_URL.matcher(text);
        if (m.find()) {
            var url = m.group(1);
            while (url.endsWith(")") || url.endsWith(".") || url.endsWith(",")) {
                url = url.substring(0, url.length() - 1);
            }
            return Optional.of(url);
        }
        return Optional.empty();
    }
}
