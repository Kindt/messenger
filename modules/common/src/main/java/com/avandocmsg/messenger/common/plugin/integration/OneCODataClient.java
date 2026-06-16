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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** 1C:Enterprise OData catalog (live Basic auth or mock fixture). */
public final class OneCODataClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    private OneCODataClient() {}

    public record CatalogItem(String code, String description) {}

    public static List<CatalogItem> fetchCatalogTop(int limit) throws Exception {
        if (IntegrationEnv.useMock(oneCLiveConfigured())) {
            return parseCatalog(fetchJson(IntegrationEnv.mockApiBase() + "/1c/odata/Catalog_Items.json"));
        }
        var base = IntegrationEnv.trimSlash(IntegrationEnv.getenv("ONEC_BASE_URL"));
        var entity = IntegrationEnv.getenv("ONEC_CATALOG_ENTITY");
        if (entity.isBlank()) {
            entity = "Catalog_Номенклатура";
        }
        var url = base + "/odata/standard.odata/" + entity + "?$top=" + Math.max(1, limit);
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(25))
            .header("Accept", "application/json")
            .GET();
        applyBasicAuth(builder);
        var response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("1C OData HTTP " + response.statusCode());
        }
        return parseCatalog(MAPPER.readTree(response.body()));
    }

    public static JsonNode fetchDocument(String docType, String number) throws Exception {
        if (IntegrationEnv.useMock(oneCLiveConfigured())) {
            var path = "/1c/odata/Documents_" + URLEncoder.encode(docType, StandardCharsets.UTF_8)
                + "_" + URLEncoder.encode(number, StandardCharsets.UTF_8) + ".json";
            return fetchJson(IntegrationEnv.mockApiBase() + path);
        }
        var base = IntegrationEnv.trimSlash(IntegrationEnv.getenv("ONEC_BASE_URL"));
        var url = base + "/odata/standard.odata/Document_" + docType + "?$filter=Number eq '" + number + "'&$top=1";
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(25))
            .header("Accept", "application/json")
            .GET();
        applyBasicAuth(builder);
        var response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("1C OData HTTP " + response.statusCode());
        }
        return MAPPER.readTree(response.body());
    }

    public static String formatCatalogMarkdown(List<CatalogItem> items) {
        if (items.isEmpty()) {
            return "Справочник пуст";
        }
        var lines = new ArrayList<String>();
        lines.add("**1С справочник (top):**");
        for (var item : items) {
            lines.add("- `" + item.code() + "` — " + item.description());
        }
        return String.join("\n", lines);
    }

    private static boolean oneCLiveConfigured() {
        return IntegrationEnv.isSet("ONEC_BASE_URL");
    }

    private static List<CatalogItem> parseCatalog(JsonNode root) {
        var out = new ArrayList<CatalogItem>();
        var value = root.path("value");
        if (!value.isArray()) {
            if (root.has("Code")) {
                out.add(new CatalogItem(root.path("Code").asText("?"), root.path("Description").asText("?")));
            }
            return out;
        }
        for (JsonNode node : value) {
            out.add(new CatalogItem(
                node.path("Code").asText(node.path("Ref_Key").asText("?")),
                node.path("Description").asText(node.path("Наименование").asText("?"))
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

    private static void applyBasicAuth(HttpRequest.Builder builder) {
        var user = IntegrationEnv.getenv("ONEC_USER");
        var pass = IntegrationEnv.getenv("ONEC_PASSWORD");
        if (user.isBlank()) {
            return;
        }
        var token = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        builder.header("Authorization", "Basic " + token);
    }
}
