package com.avandocmsg.messenger.web;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Отдаёт небольшой JS с публичным URL WebSocket (ws-gateway), т.к. встроенный Tomcat не проксирует upgrade.
 */
final class WebClientEnvServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String wsUrl = System.getenv().getOrDefault("WEB_CLIENT_WS_PUBLIC_URL", "ws://127.0.0.1:8081/ws")
            .trim()
            .replaceAll("/$", "");
        String body = "window.__WEB_CLIENT__ = { wsUrl: " + jsonQuote(wsUrl) + " };\n";
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/javascript;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store, max-age=0");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonQuote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
