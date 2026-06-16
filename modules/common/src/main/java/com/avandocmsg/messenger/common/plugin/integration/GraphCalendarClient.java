package com.avandocmsg.messenger.common.plugin.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Microsoft Graph calendarView (live token or mock fixture). */
public final class GraphCalendarClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    private GraphCalendarClient() {}

    public record CalendarEvent(String subject, String startDateTime) {}

    public static List<CalendarEvent> fetchUpcoming() throws Exception {
        if (IntegrationEnv.useMock(graphLiveConfigured())) {
            return parseEvents(fetchJson(IntegrationEnv.mockApiBase() + "/exchange/v1.0/me/calendarview.json"));
        }
        var token = resolveAccessToken();
        if (token.isBlank()) {
            throw new IllegalStateException("Graph live mode requires GRAPH_ACCESS_TOKEN or client credentials");
        }
        var start = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var end = start.plus(7, ChronoUnit.DAYS);
        var url = "https://graph.microsoft.com/v1.0/me/calendarView"
            + "?startDateTime=" + enc(start.toString())
            + "&endDateTime=" + enc(end.toString())
            + "&$top=10";
        var request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/json")
            .GET()
            .build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Graph HTTP " + response.statusCode());
        }
        return parseEvents(MAPPER.readTree(response.body()));
    }

    public static String formatMarkdown(List<CalendarEvent> events) {
        if (events.isEmpty()) {
            return "Календарь пуст";
        }
        var lines = new ArrayList<String>();
        lines.add("**Ближайшие события:**");
        for (var e : events) {
            lines.add("- **" + e.subject() + "** (" + e.startDateTime() + ")");
        }
        return String.join("\n", lines);
    }

    private static boolean graphLiveConfigured() {
        return IntegrationEnv.isSet("GRAPH_ACCESS_TOKEN")
            || (IntegrationEnv.isSet("GRAPH_TENANT_ID")
            && IntegrationEnv.isSet("GRAPH_CLIENT_ID")
            && IntegrationEnv.isSet("GRAPH_CLIENT_SECRET"));
    }

    private static String resolveAccessToken() throws Exception {
        if (IntegrationEnv.isSet("GRAPH_ACCESS_TOKEN")) {
            return IntegrationEnv.getenv("GRAPH_ACCESS_TOKEN");
        }
        var tenant = IntegrationEnv.getenv("GRAPH_TENANT_ID");
        var clientId = IntegrationEnv.getenv("GRAPH_CLIENT_ID");
        var secret = IntegrationEnv.getenv("GRAPH_CLIENT_SECRET");
        if (tenant.isBlank() || clientId.isBlank() || secret.isBlank()) {
            return "";
        }
        var body = "client_id=" + enc(clientId)
            + "&client_secret=" + enc(secret)
            + "&scope=" + enc("https://graph.microsoft.com/.default")
            + "&grant_type=client_credentials";
        var request = HttpRequest.newBuilder()
            .uri(URI.create("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Graph token HTTP " + response.statusCode());
        }
        return MAPPER.readTree(response.body()).path("access_token").asText("");
    }

    private static List<CalendarEvent> parseEvents(JsonNode root) {
        var out = new ArrayList<CalendarEvent>();
        var value = root.path("value");
        if (!value.isArray()) {
            return out;
        }
        for (JsonNode node : value) {
            out.add(new CalendarEvent(
                node.path("subject").asText("?"),
                node.path("start").path("dateTime").asText("?")
            ));
        }
        return out;
    }

    private static JsonNode fetchJson(String url) throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return MAPPER.readTree(response.body());
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
