package com.avandocmsg.messenger.worker.exchangebridge;

import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.avandocmsg.messenger.common.plugin.PluginResponse;
import com.avandocmsg.messenger.common.plugin.integration.GraphCalendarClient;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

/** Spec 014 P2: MS Exchange / Graph calendar bridge (mock-backed demo). */
public final class ExchangeBridgeWorker {
    private static final Logger log = LoggerFactory.getLogger(ExchangeBridgeWorker.class);

    private ExchangeBridgeWorker() {}

    public static void main(String[] args) throws Exception {
        UserMessageSource messages = WorkerMessageSources.forWorker(
            ExchangeBridgeWorker.class,
            "com.avandocmsg.messenger.i18n.messages_worker_exchange_bridge");
        int port = parsePort(System.getenv("EXCHANGE_BRIDGE_PORT"), 8093);
        int healthPort = parsePort(System.getenv("EXCHANGE_BRIDGE_METRICS_PORT"), 9193);

        var pluginServer = startPluginServer(port);
        try (var health = WorkerHealthHttpServer.startHealthOnly(
            healthPort, "exchange-bridge-health", () -> true, messages)) {
            log.info("exchange-bridge plugin HTTP on :{} health on :{}", port, health.getPort());
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
                var event = new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, PluginEvent.class);
                var response = handle(event);
                var json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(response);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length);
                exchange.getResponseBody().write(json);
            } catch (Exception e) {
                log.warn("exchange handle failed: {}", e.getMessage());
                var err = "{\"error\":\"handle_failed\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, err.length);
                exchange.getResponseBody().write(err);
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newFixedThreadPool(4, r -> {
            var t = new Thread(r, "exchange-bridge-http");
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
            return PluginResponse.text("pong (exchange-bridge)");
        }
        if (lower.startsWith("/calendar") || lower.startsWith("/freebusy") || isExchangePreset(event)) {
            return calendarResponse();
        }
        return PluginResponse.text("Exchange bridge. Команды: `ping`, `/calendar`, `/freebusy`");
    }

    private static boolean isExchangePreset(PluginEvent event) {
        Map<String, Object> snap = event.configSnapshot();
        if (snap == null) {
            return false;
        }
        return "exchange-bridge".equals(String.valueOf(snap.get("preset_id")));
    }

    private static PluginResponse calendarResponse() {
        try {
            return PluginResponse.text(GraphCalendarClient.formatMarkdown(GraphCalendarClient.fetchUpcoming()));
        } catch (Exception e) {
            return PluginResponse.text("Exchange недоступен: " + e.getMessage());
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
