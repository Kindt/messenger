package com.avandocmsg.messenger.worker.storagebridge;

import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.avandocmsg.messenger.common.plugin.PluginButton;
import com.avandocmsg.messenger.common.plugin.PluginCard;
import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.avandocmsg.messenger.common.plugin.PluginResponse;
import com.avandocmsg.messenger.common.plugin.integration.WebDavStorageClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** Spec 014 P2: file storage bridge (WebDAV/SMB mock search demo). */
public final class StorageBridgeWorker {
    private static final Logger log = LoggerFactory.getLogger(StorageBridgeWorker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StorageBridgeWorker() {}

    public static void main(String[] args) throws Exception {
        UserMessageSource messages = WorkerMessageSources.forWorker(
            StorageBridgeWorker.class,
            "com.avandocmsg.messenger.i18n.messages_worker_storage_bridge");
        int port = parsePort(System.getenv("STORAGE_BRIDGE_PORT"), 8094);
        int healthPort = parsePort(System.getenv("STORAGE_BRIDGE_METRICS_PORT"), 9194);

        var pluginServer = startPluginServer(port, messages);
        try (var health = WorkerHealthHttpServer.startHealthOnly(
            healthPort, "storage-bridge-health", () -> true, messages)) {
            log.info(messages.format("bridge.http_started", port, health.getPort()));
            Thread.currentThread().join();
        } finally {
            pluginServer.stop(0);
        }
    }

    static HttpServer startPluginServer(int port) throws IOException {
        return startPluginServer(port, null);
    }

    static HttpServer startPluginServer(int port, UserMessageSource messages) throws IOException {
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
                if (messages != null) {
                    log.warn(messages.format("bridge.handle_failed", e.getMessage()));
                } else {
                    log.warn("storage handle failed: {}", e.getMessage());
                }
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
            return searchResponse(query);
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

    private static PluginResponse searchResponse(String query) {
        try {
            return PluginResponse.text(WebDavStorageClient.formatMarkdown(WebDavStorageClient.search(query)));
        } catch (Exception e) {
            return PluginResponse.text("Storage недоступен: " + e.getMessage());
        }
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
