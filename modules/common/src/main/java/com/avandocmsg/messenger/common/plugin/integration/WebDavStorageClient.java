package com.avandocmsg.messenger.common.plugin.integration;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.http.HttpClientSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** WebDAV PROPFIND search (live) or mock JSON search. */
public final class WebDavStorageClient {
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final HttpClient HTTP = HttpClientSupport.sharedFollowingRedirects();
    private static final Pattern HREF = Pattern.compile("<(?:D:)?href>([^<]+)</(?:D:)?href>", Pattern.CASE_INSENSITIVE);

    private WebDavStorageClient() {}

    public record StorageItem(String name, String path) {}

    public static List<StorageItem> search(String query) throws Exception {
        if (IntegrationEnv.useMock(webDavLiveConfigured())) {
            return parseMock(fetchMockJson(query));
        }
        return propfindFilter(query);
    }

    public static String formatMarkdown(List<StorageItem> items) {
        if (items.isEmpty()) {
            return "Файлы не найдены";
        }
        var lines = new ArrayList<String>();
        lines.add("**Результаты поиска:**");
        for (var item : items) {
            lines.add("- **" + item.name() + "** → `" + item.path() + "`");
        }
        return String.join("\n", lines);
    }

    private static boolean webDavLiveConfigured() {
        return IntegrationEnv.isSet("WEBDAV_BASE_URL");
    }

    private static JsonNode fetchMockJson(String query) throws Exception {
        var q = query.isBlank() ? "report" : query;
        var url = IntegrationEnv.mockApiBase() + "/storage/v1/search.json?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
        var request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return MAPPER.readTree(response.body());
    }

    private static List<StorageItem> parseMock(JsonNode json) {
        var out = new ArrayList<StorageItem>();
        var items = json.path("items");
        if (!items.isArray()) {
            return out;
        }
        for (JsonNode node : items) {
            out.add(new StorageItem(node.path("name").asText("?"), node.path("path").asText("?")));
        }
        return out;
    }

    private static List<StorageItem> propfindFilter(String query) throws IOException, InterruptedException {
        var base = IntegrationEnv.trimSlash(IntegrationEnv.getenv("WEBDAV_BASE_URL"));
        var body = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
              <d:prop><d:displayname/></d:prop>
            </d:propfind>
            """;
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(base.endsWith("/") ? base : base + "/"))
            .timeout(Duration.ofSeconds(20))
            .header("Depth", "1")
            .header("Content-Type", "application/xml; charset=utf-8")
            .method("PROPFIND", HttpRequest.BodyPublishers.ofString(body));
        applyBasicAuth(builder);
        var response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 400) {
            throw new IllegalStateException("WebDAV PROPFIND HTTP " + response.statusCode());
        }
        var needle = query.isBlank() ? "" : query.toLowerCase(Locale.ROOT);
        var out = new ArrayList<StorageItem>();
        Matcher m = HREF.matcher(response.body());
        while (m.find() && out.size() < 10) {
            var href = m.group(1);
            if (!href.endsWith("/")) {
                var name = href.contains("/") ? href.substring(href.lastIndexOf('/') + 1) : href;
                if (needle.isBlank() || name.toLowerCase(Locale.ROOT).contains(needle)) {
                    out.add(new StorageItem(name, href));
                }
            }
        }
        return out;
    }

    private static void applyBasicAuth(HttpRequest.Builder builder) {
        var user = IntegrationEnv.getenv("WEBDAV_USER");
        var pass = IntegrationEnv.getenv("WEBDAV_PASSWORD");
        if (user.isBlank()) {
            return;
        }
        var token = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        builder.header("Authorization", "Basic " + token);
    }
}
