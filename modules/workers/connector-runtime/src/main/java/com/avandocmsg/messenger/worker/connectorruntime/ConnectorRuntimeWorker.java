package com.avandocmsg.messenger.worker.connectorruntime;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.avandocmsg.messenger.common.plugin.PluginButton;
import com.avandocmsg.messenger.common.plugin.PluginCard;
import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.avandocmsg.messenger.common.plugin.PluginMessage;
import com.avandocmsg.messenger.common.plugin.PluginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.prometheus.client.hotspot.DefaultExports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Spec 014: universal connector runtime вЂ” Plugin Runtime API v1 on integrations VM.
 */
public final class ConnectorRuntimeWorker {
    private static final Logger log = LoggerFactory.getLogger(ConnectorRuntimeWorker.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private ConnectorRuntimeWorker() {}

    public static void main(String[] args) throws Exception {
        UserMessageSource messages = WorkerMessageSources.forWorker(
            ConnectorRuntimeWorker.class,
            "com.avandocmsg.messenger.i18n.messages_worker_connector_runtime");
        int port = parsePort(System.getenv("CONNECTOR_RUNTIME_PORT"), 8091);
        int healthPort = parsePort(System.getenv("CONNECTOR_RUNTIME_METRICS_PORT"), 9198);

        DefaultExports.initialize();
        var pluginServer = startPluginServer(port, messages);
        ConnectorRuntimeMetricsHttpServer.HealthProbe pluginUp = () -> pluginServer.getAddress() != null;
        try (var metrics = ConnectorRuntimeMetricsHttpServer.start(healthPort, pluginUp, messages)) {
            log.info(messages.format("connector.http_started", port, metrics.getPort()));
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
                    log.warn(messages.format("connector.handle_failed", e.getMessage()));
                } else {
                    log.warn("plugin handle failed: {}", e.getMessage());
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
            var t = new Thread(r, "connector-runtime-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        return server;
    }

    static PluginResponse handle(PluginEvent event) {
        var profile = ConnectorProfileSupport.tryProfile(event);
        if (profile.isPresent()) {
            return profile.get();
        }
        var type = event.type() != null ? event.type() : "";
        if ("button".equals(type)) {
            var buttonId = payloadString(event, "button_id");
            if ("about".equals(buttonId)) {
                return PluginResponse.text("Korus connector-runtime v1\nhttps://korus.local");
            }
            if ("tip".equals(buttonId)) {
                return PluginResponse.text("РЎР»СѓС‡Р°Р№РЅС‹Р№ СЃРѕРІРµС‚: РїСЂРѕРІРµСЂСЊС‚Рµ smoke-plugin-echo.");
            }
        }
        if ("slash".equals(type) && event.text() != null && event.text().startsWith("/echo ")) {
            return PluginResponse.text(event.text().substring(6).trim());
        }
        var text = event.text() != null ? event.text().trim().toLowerCase() : "";
        if ("ping".equals(text) || "ping".equals(text.replace("@", ""))) {
            return PluginResponse.text("pong (connector-runtime)");
        }
        return new PluginResponse(
            List.of(PluginMessage.markdown("Echo menu (connector-runtime). РљРѕРјР°РЅРґС‹: `ping`, `/echo <text>`")),
            List.of(new PluginCard(
                "РњРµРЅСЋ",
                null,
                List.of(
                    new PluginButton("about", "Рћ РїСЂРѕРµРєС‚Рµ"),
                    new PluginButton("tip", "РЎР»СѓС‡Р°Р№РЅС‹Р№ СЃРѕРІРµС‚")
                )
            )),
            null
        );
    }

    private static String payloadString(PluginEvent event, String key) {
        Map<String, Object> payload = event.payload();
        if (payload == null) {
            return null;
        }
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
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
