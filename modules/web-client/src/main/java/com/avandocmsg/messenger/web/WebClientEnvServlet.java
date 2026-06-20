package com.avandocmsg.messenger.web;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Отдаёт небольшой JS: публичный URL WebSocket (ws-gateway) и опционально JSON-массив ICE-серверов для WebRTC.
 * ICE: переменная {@code WEB_CLIENT_RTC_ICE_SERVERS} — строка JSON (массив объектов {@code urls} / {@code username} / {@code credential}),
 * например {@code [{"urls":"stun:stun.l.google.com:19302"},{"urls":"turn:…","username":"…","credential":"…"}]}.
 */
final class WebClientEnvServlet extends HttpServlet {
    /**
     * Собирает тело скрипта (как в {@link #doGet}). {@code getenv} возвращает {@code null}, если переменной нет.
     * Пакетный доступ — для юнит-тестов без подмены {@link System#getenv()}.
     */
    static String buildEnvScriptBody(Function<String, String> getenv) {
        String wsUrl = envOrDefault(getenv, "WEB_CLIENT_WS_PUBLIC_URL", "ws://127.0.0.1:8081/ws")
            .replaceAll("/$", "");
        String iceRaw = envOrDefault(getenv, "WEB_CLIENT_RTC_ICE_SERVERS", "");
        String iceJs = iceRaw.isEmpty() ? "null" : jsonQuote(iceRaw);
        String vapidRaw = envOrDefault(getenv, "WEB_CLIENT_VAPID_PUBLIC_KEY", "");
        String vapidJs = vapidRaw.isEmpty() ? "null" : jsonQuote(vapidRaw);
        String watermarkRaw = envOrDefault(getenv, "APP_WATERMARK_TEXT", "");
        String watermarkJs = watermarkRaw.isEmpty() ? "null" : jsonQuote(watermarkRaw);
        boolean disableServiceWorker = envFlag(getenv, "WEB_CLIENT_DISABLE_SW");
        return "window.__WEB_CLIENT__ = { wsUrl: "
            + jsonQuote(wsUrl)
            + ", iceServersJson: "
            + iceJs
            + ", vapidPublicKey: "
            + vapidJs
            + ", watermarkText: "
            + watermarkJs
            + ", disableServiceWorker: "
            + disableServiceWorker
            + " };\n";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = buildEnvScriptBody(System::getenv);
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/javascript;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store, max-age=0");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonQuote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String envOrDefault(Function<String, String> getenv, String key, String fallback) {
        String value = getenv.apply(key);
        if (value == null) {
            return fallback;
        }
        return value.trim();
    }

    private static boolean envFlag(Function<String, String> getenv, String key) {
        String value = getenv.apply(key);
        return value != null && ("1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim()));
    }
}
