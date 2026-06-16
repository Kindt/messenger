package com.avandocmsg.messenger.worker.connectorruntime;

import com.avandocmsg.messenger.common.plugin.PluginResponse;
import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class ConnectorProfileSupport {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ISSUE_KEY = Pattern.compile("([A-Z][A-Z0-9]+-\\d+)");
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    private ConnectorProfileSupport() {}

    static Optional<PluginResponse> tryProfile(PluginEvent event) {
        var preset = presetId(event);
        if (preset == null) {
            return Optional.empty();
        }
        return switch (preset) {
            case "jira-connector" -> Optional.of(handleJira(event));
            case "confluence-connector" -> Optional.of(handleConfluence(event));
            case "naumen-sd" -> Optional.of(handleNaumen(event));
            default -> Optional.empty();
        };
    }

    private static String presetId(PluginEvent event) {
        Map<String, Object> snap = event.configSnapshot();
        if (snap == null) {
            return null;
        }
        Object v = snap.get("preset_id");
        return v != null ? v.toString() : null;
    }

    private static PluginResponse handleJira(PluginEvent event) {
        var text = event.text() != null ? event.text().trim() : "";
        var key = extractIssueKey(text);
        if (key == null) {
            return PluginResponse.text("Jira: укажите ключ задачи, например `/jira OPS-1` или `OPS-1`");
        }
        try {
            var json = fetchJson(mockBase() + "/jira/rest/api/2/issue/" + key + ".json");
            var fields = json.path("fields");
            var summary = fields.path("summary").asText("?");
            var status = fields.path("status").path("name").asText("?");
            return PluginResponse.text("**" + key + "**: " + summary + "\nСтатус: **" + status + "**");
        } catch (Exception e) {
            return PluginResponse.text("Jira mock недоступен: " + e.getMessage());
        }
    }

    private static PluginResponse handleConfluence(PluginEvent event) {
        var query = confluenceQuery(event);
        if (query.isBlank()) {
            return PluginResponse.text("Confluence: `/wiki <запрос>` или текст поиска");
        }
        try {
            var json = fetchJson(mockBase() + "/confluence/rest/api/content/search?q=" + urlEncode(query));
            var results = json.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return PluginResponse.text("Ничего не найдено по запросу: " + query);
            }
            var first = results.get(0);
            var title = first.path("title").asText("?");
            var url = first.path("_links").path("webui").asText("");
            return PluginResponse.text("**" + title + "**\n" + (url.isBlank() ? "" : url));
        } catch (Exception e) {
            return PluginResponse.text("Confluence mock недоступен: " + e.getMessage());
        }
    }

    private static PluginResponse handleNaumen(PluginEvent event) {
        var text = event.text() != null ? event.text().trim() : "";
        var ticketId = text.replaceFirst("(?i)^/naumen\\s+", "").trim();
        if (ticketId.isBlank() || ticketId.equalsIgnoreCase(text)) {
            ticketId = "INC-1001";
        }
        try {
            var json = fetchJson(mockBase() + "/naumen/api/tickets/" + urlEncode(ticketId) + ".json");
            var status = json.path("status").asText("?");
            var subject = json.path("subject").asText("?");
            return PluginResponse.text("Заявка **" + ticketId + "**: " + subject + "\nСтатус: **" + status + "**");
        } catch (Exception e) {
            return PluginResponse.text("Naumen mock недоступен: " + e.getMessage());
        }
    }

    private static String confluenceQuery(PluginEvent event) {
        var text = event.text() != null ? event.text().trim() : "";
        if (text.toLowerCase().startsWith("/wiki ")) {
            return text.substring(6).trim();
        }
        if (text.toLowerCase().startsWith("/confluence ")) {
            return text.substring(12).trim();
        }
        return text;
    }

    private static String extractIssueKey(String text) {
        if (text.toLowerCase().startsWith("/jira ")) {
            text = text.substring(6).trim();
        }
        var m = ISSUE_KEY.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static JsonNode fetchJson(String url) throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return MAPPER.readTree(response.body());
    }

    private static String mockBase() {
        var base = System.getenv("MOCK_API_BASE");
        if (base == null || base.isBlank()) {
            return "http://mock-apis:8080";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
