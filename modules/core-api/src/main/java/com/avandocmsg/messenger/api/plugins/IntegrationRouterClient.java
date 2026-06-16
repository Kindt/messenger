package com.avandocmsg.messenger.api.plugins;

import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.avandocmsg.messenger.common.plugin.PluginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class IntegrationRouterClient {
    private static final Logger log = LoggerFactory.getLogger(IntegrationRouterClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String defaultRuntimeBaseUrl;

    public IntegrationRouterClient(String defaultRuntimeBaseUrl) {
        this.defaultRuntimeBaseUrl = trimTrailingSlash(defaultRuntimeBaseUrl);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public PluginResponse forward(PluginEvent event, String runtimeEndpoint) throws Exception {
        var base = runtimeEndpoint != null && !runtimeEndpoint.isBlank()
            ? trimTrailingSlash(runtimeEndpoint)
            : defaultRuntimeBaseUrl;
        var url = base + "/v1/plugin/handle";
        var body = MAPPER.writeValueAsString(event);
        var request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("plugin runtime {} returned {}", url, response.statusCode());
            throw new IntegrationRouterException("error.plugin.runtime_unavailable");
        }
        return MAPPER.readValue(response.body(), PluginResponse.class);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static final class IntegrationRouterException extends Exception {
        public IntegrationRouterException(String messageKey) {
            super(messageKey);
        }
    }
}
