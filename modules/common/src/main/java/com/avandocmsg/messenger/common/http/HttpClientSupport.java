package com.avandocmsg.messenger.common.http;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Shared {@link HttpClient} instances for long-running workers and integration adapters (FR-098, FR-114).
 * Reuses JDK connection pools instead of ad-hoc {@code HttpClient.newBuilder().build()} per class.
 */
public final class HttpClientSupport {

    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REDIRECT_CONNECT_TIMEOUT = Duration.ofSeconds(8);

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(namedDaemonFactory("http-client"));

    private static final HttpClient SHARED = buildDefault();
    private static final HttpClient SHARED_FOLLOWING_REDIRECTS = buildFollowingRedirects();

    private HttpClientSupport() {
    }

    /** Default outbound client: 10s connect timeout, no redirects. */
    public static HttpClient sharedClient() {
        return SHARED;
    }

    /** Outbound client for WebDAV and similar endpoints that may redirect. */
    public static HttpClient sharedFollowingRedirects() {
        return SHARED_FOLLOWING_REDIRECTS;
    }

    /**
     * Pre-configured builder sharing the worker executor. {@code build()} creates a separate pool — prefer
     * {@link #sharedClient()} for production hot paths.
     */
    public static HttpClient.Builder clientBuilder() {
        return HttpClient.newBuilder()
            .executor(EXECUTOR)
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT);
    }

    public static HttpClient.Builder clientBuilder(Duration connectTimeout) {
        return HttpClient.newBuilder()
            .executor(EXECUTOR)
            .connectTimeout(connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT);
    }

    private static HttpClient buildDefault() {
        return clientBuilder().build();
    }

    private static HttpClient buildFollowingRedirects() {
        return HttpClient.newBuilder()
            .executor(EXECUTOR)
            .connectTimeout(REDIRECT_CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        return r -> {
            var t = new Thread(r, prefix);
            t.setDaemon(true);
            return t;
        };
    }
}
