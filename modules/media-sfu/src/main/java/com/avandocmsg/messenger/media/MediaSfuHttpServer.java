package com.avandocmsg.messenger.media;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MediaSfuHttpServer implements AutoCloseable {

    private final String nodeId;
    private final InMemoryMediaRoomService rooms;
    private final HttpServer server;
    private final ExecutorService executor;

    public MediaSfuHttpServer(int port, String nodeId, InMemoryMediaRoomService rooms) throws IOException {
        this.nodeId = requireNodeId(nodeId);
        this.rooms = Objects.requireNonNull(rooms, "rooms");
        server = HttpServer.create(new InetSocketAddress(port), 32);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/health", this::health);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        var body = ("{\"status\":\"UP\",\"node_id\":\"" + json(nodeId)
            + "\",\"active_rooms\":" + rooms.activeRoomCount() + "}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static String requireNodeId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("nodeId required");
        }
        return value;
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
