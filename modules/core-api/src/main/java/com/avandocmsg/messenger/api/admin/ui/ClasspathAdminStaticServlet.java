package com.avandocmsg.messenger.api.admin.ui;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Раздаёт статические файлы встроенной админ-консоли из classpath ({@code admin-ui/}).
 */
public final class ClasspathAdminStaticServlet extends HttpServlet {

    private static final String RESOURCE_PREFIX = "admin-ui/";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String relative = relativeAssetPath(req.getPathInfo());
        if (relative == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String resourcePath = RESOURCE_PREFIX + relative;
        ClassLoader cl = ClasspathAdminStaticServlet.class.getClassLoader();
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

    /** Логика пути для маппинга {@code /admin} и {@code /admin/*} (pathInfo относительно сервлета). */
    static String relativeAssetPath(String pathInfo) {
        if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
            return "index.html";
        }
        if (pathInfo.contains("..")) {
            return null;
        }
        String trimmed = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        return trimmed.isEmpty() ? "index.html" : trimmed;
    }

    static String contentType(String relative) {
        String lower = relative.toLowerCase();
        if (lower.endsWith(".html")) {
            return "text/html;charset=UTF-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css;charset=UTF-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript;charset=UTF-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".ico")) {
            return "image/x-icon";
        }
        return "application/octet-stream";
    }

    /** HTML без кэша (обновления SPA); статика JS/CSS — короткий кэш для снижения нагрузки. */
    static String cacheControl(String relative) {
        String lower = relative.toLowerCase();
        if (lower.endsWith(".html")) {
            return "no-store, max-age=0";
        }
        if (lower.endsWith(".js") || lower.endsWith(".css")) {
            return "public, max-age=3600";
        }
        return "public, max-age=86400";
    }
}
