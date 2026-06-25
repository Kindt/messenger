package com.avandocmsg.messenger.worker.onecbridge;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.avandocmsg.messenger.common.plugin.PluginResponse;
import com.avandocmsg.messenger.common.plugin.integration.OneCODataClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Spec 014: 1C:Enterprise OData bridge (mock or live). */
public final class OneCBridgeWorker {
    private static final Logger log = LoggerFactory.getLogger(OneCBridgeWorker.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final Pattern DOC = Pattern.compile("([A-Za-z\u0410-\u042F\u0430-\u044F0-9_]+)-(\\d+)");

    private OneCBridgeWorker() {}

    public static void main(String[] args) throws Exception {
        UserMessageSource messages = WorkerMessageSources.forWorker(
            OneCBridgeWorker.class,
            "com.avandocmsg.messenger.i18n.messages_worker_onec_bridge");
        int port = parsePort(System.getenv("ONEC_BRIDGE_PORT"), 8097);
        int healthPort = parsePort(System.getenv("ONEC_BRIDGE_METRICS_PORT"), 9197);

        var pluginServer = startPluginServer(port, messages);
        var pluginUp = (java.util.function.BooleanSupplier) () -> pluginServer.getAddress() != null;
        try (var health = WorkerHealthHttpServer.startHealthOnly(
            healthPort, "onec-bridge-health", pluginUp, messages)) {
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
                    log.warn("onec handle failed: {}", e.getMessage());
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
            var t = new Thread(r, "onec-bridge-http");
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
            return PluginResponse.text("pong (onec-bridge)");
        }
        if (lower.startsWith("/catalog") || lower.equals("/1c") || isOneCPreset(event)) {
            return catalogResponse();
        }
        if (lower.startsWith("/doc ")) {
            return documentResponse(text.substring(5).trim());
        }
        return PluginResponse.text("1C bridge. РљРѕРјР°РЅРґС‹: `ping`, `/catalog`, `/doc <Type>-<Number>`");
    }

    private static boolean isOneCPreset(PluginEvent event) {
        Map<String, Object> snap = event.configSnapshot();
        return snap != null && "1c-bridge".equals(String.valueOf(snap.get("preset_id")));
    }

    private static PluginResponse catalogResponse() {
        try {
            return PluginResponse.text(OneCODataClient.formatCatalogMarkdown(OneCODataClient.fetchCatalogTop(5)));
        } catch (Exception e) {
            return PluginResponse.text("1C OData РЅРµРґРѕСЃС‚СѓРїРµРЅ: " + e.getMessage());
        }
    }

    private static PluginResponse documentResponse(String arg) {
        Matcher m = DOC.matcher(arg);
        if (!m.matches()) {
            return PluginResponse.text("Р¤РѕСЂРјР°С‚: `/doc SalesOrder-1001`");
        }
        try {
            var json = OneCODataClient.fetchDocument(m.group(1), m.group(2));
            var value = json.path("value");
            var node = value.isArray() && !value.isEmpty() ? value.get(0) : json;
            var number = node.path("Number").asText(arg);
            var posted = node.path("Posted").asText(node.path("РџСЂРѕРІРµРґРµРЅ").asText("?"));
            return PluginResponse.text("Р”РѕРєСѓРјРµРЅС‚ **" + number + "** вЂ” РїСЂРѕРІРµРґС‘РЅ: **" + posted + "**");
        } catch (Exception e) {
            return PluginResponse.text("1C РґРѕРєСѓРјРµРЅС‚ РЅРµРґРѕСЃС‚СѓРїРµРЅ: " + e.getMessage());
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
