package com.avandocmsg.messenger.worker.storagebridge;

import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.avandocmsg.messenger.common.plugin.PluginButton;
import com.avandocmsg.messenger.common.plugin.PluginCard;
import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.avandocmsg.messenger.common.plugin.PluginResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** Spec 014 P2: file storage bridge (WebDAV/SMB mock search demo). */
public final class StorageBridgeWorker {
    private static final Logger log = LoggerFactory.getLogger(StorageBridgeWorker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private StorageBridgeWorker() {}

    public static void main(String[] args) throws Exception {
        UserMessageSource messages = WorkerMessageSources.forWorker(
            StorageBridgeWorker.class,
            "com.avandocmsg.messenger.i18n.messages_worker_storage_bridge");
        int port = parsePort(System.getenv("STORAGE_BRIDGE_PORT"), 8094);
        int healthPort = parsePort(System.getenv("STORAGE_BRIDGE_METRICS_PORT"), 9194);

        var pluginServer = startPluginServer(port);
        try (var health = WorkerHealthHttpServer.startHealthOnly(
            healthPort, "storage-bridge-health", () -> true, messages)) {
            log.info("storage-bridge plugin HTTP on :{} health on :{}", port, health.getPort());
            Thread.currentThread().join();
        } finally {
            pluginServer.stop(0);
        }
    }

    static HttpServer startPluginServer(int port) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(port), 8);
        server.createContext("/v1/plugin/handle", exchange -> {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                var event = MAPPER.readValue(body, PluginEvent.class);
                var response = handle(event);
                var json = MAPPER.writeValueAsBytes(response);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length);
                exchange.getResponseBody().write(json);
            } catch (Exception e) {
                log.warn("storage handle failed: {}", e.getMessage());
                var err = "{\"error\":\"handle_failed\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, err.length);
                exchange.getResponseBody().write(err);
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newFixedThreadPool(4, r -> {
            var t = new Thread(r, "storage-bridge-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        return server;
    }

    static PluginResponse handle(PluginEvent event) {
        var text = event.text() != null ? event.text().trim() : "";
        var lower = text.toLowerCase();
        if ("ping".equals(lower)) {
            return PluginResponse.text("pong (storage-bridge)");
        }
        if (lower.startsWith("/file ") || lower.startsWith("/storage ") || isStoragePreset(event)) {
            var query = extractQuery(text);
            return searchMock(query);
        }
        return new PluginResponse(
            List.of(com.avandocmsg.messenger.common.plugin.PluginMessage.markdown(
                "Storage bridge. Команды: `ping`, `/file <query>`")),
            List.of(new PluginCard("Поиск", null, List.of(
                new PluginButton("search_report", "Найти Q1-report")
            ))),
            null
        );
    }

    private static String extractQuery(String text) {
        if (text.toLowerCase().startsWith("/file ")) {
            return text.substring(6).trim();
        }
        if (text.toLowerCase().startsWith("/storage ")) {
            return text.substring(9).trim();
        }
        return text;
    }

    private static boolean isStoragePreset(PluginEvent event) {
        Map<String, Object> snap = event.configSnapshot();
        return snap != null && "storage-bridge".equals(String.valueOf(snap.get("preset_id")));
    }

    private static PluginResponse searchMock(String query) {
        try {
            var q = query.isBlank() ? "report" : query;
            var json = fetchJson(mockBase() + "/storage/v1/search.json?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
            var items = json.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return PluginResponse.text("Файлы не найдены: " + q);
            }
            var lines = new ArrayList<String>();
            for (JsonNode item : items) {
                lines.add("- **" + item.path("name").asText("?") + "** → `" + item.path("path").asText("?") + "`");
            }
            return PluginResponse.text("**Результаты поиска:**\n" + String.join("\n", lines));
        } catch (Exception e) {
            return PluginResponse.text("Storage mock недоступен: " + e.getMessage());
        }
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

    private static int parsePort(String raw, int defaultPort) {
        if (raw == null || raw.isBlank()) {
            return defaultPort;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }
}
