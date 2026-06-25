package com.avandocmsg.messenger.web;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Раздаёт статику веб-клиента из classpath ({@code webui/}).
 */
public final class ClasspathWebUiServlet extends HttpServlet {

    private static final String RESOURCE_PREFIX = "webui/";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String relative = relativeAssetPath(req);
        if (relative == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String resourcePath = RESOURCE_PREFIX + relative;
        ClassLoader cl = ClasspathWebUiServlet.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            resp.setCharacterEncoding("UTF-8");
            resp.setContentType(contentType(relative));
            resp.setHeader("X-Content-Type-Options", "nosniff");
            resp.setHeader("Cache-Control", cacheControl(relative));
            try (OutputStream out = resp.getOutputStream()) {
                in.transferTo(out);
            }
        }
    }

    /**
     * Путь относительно контекста; служебные URL не обслуживаются этим сервлетом.
     */
    static String relativeAssetPath(HttpServletRequest req) {
        String uri = req.getRequestURI();
        if (uri == null) {
            return null;
        }
        String cp = req.getContextPath();
        if (cp != null && !cp.isEmpty() && uri.startsWith(cp)) {
            uri = uri.substring(cp.length());
        }
        if (uri.isEmpty() || "/".equals(uri)) {
            return "index.html";
        }
        if (uri.startsWith("/api") || "/health".equals(uri) || "/nginx-health".equals(uri)
            || "/web-client-env.js".equals(uri)) {
            return null;
        }
        if (uri.contains("..")) {
            return null;
        }
        String trimmed = uri.startsWith("/") ? uri.substring(1) : uri;
        return trimmed.isEmpty() ? "index.html" : trimmed;
    }

    static String contentType(String relative) {
        String lower = relative.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) {
            return "text/html;charset=UTF-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css;charset=UTF-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript;charset=UTF-8";
        }
        if (lower.endsWith(".json")) {
            return "application/json;charset=UTF-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".ico")) {
            return "image/x-icon";
        }
        return "application/octet-stream";
    }

    static String cacheControl(String relative) {
        String lower = relative.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")
            || "sw.js".equals(lower)
            || "app.js".equals(lower)
            || "app.bundle.js".equals(lower)
            || "themes.css".equals(lower)
            || "styles.css".equals(lower)
            || "tailwind.css".equals(lower)) {
            return "no-store, max-age=0";
        }
        return "public, max-age=86400";
    }
}
