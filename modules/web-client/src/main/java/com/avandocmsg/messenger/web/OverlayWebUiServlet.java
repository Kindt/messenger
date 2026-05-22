package com.avandocmsg.messenger.web;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Статика webui: сначала файл из {@code WEB_CLIENT_WEBUI_OVERLAY}, иначе classpath ({@code webui/}).
 */
public final class OverlayWebUiServlet extends HttpServlet {

    private static final String RESOURCE_PREFIX = "webui/";
    private final Path overlayRoot;

    public OverlayWebUiServlet(Path overlayRoot) {
        this.overlayRoot = overlayRoot.toAbsolutePath().normalize();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String relative = ClasspathWebUiServlet.relativeAssetPath(req);
        if (relative == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path overlayFile = overlayRoot.resolve(relative).normalize();
        if (overlayFile.startsWith(overlayRoot) && Files.isRegularFile(overlayFile)) {
            serveFile(overlayFile, relative, resp);
            return;
        }
        String resourcePath = RESOURCE_PREFIX + relative;
        ClassLoader cl = OverlayWebUiServlet.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            writeResource(in, relative, resp);
        }
    }

    private void serveFile(Path file, String relative, HttpServletResponse resp) throws IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType(ClasspathWebUiServlet.contentType(relative));
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("Cache-Control", ClasspathWebUiServlet.cacheControl(relative));
        try (OutputStream out = resp.getOutputStream()) {
            Files.copy(file, out);
        }
    }

    private static void writeResource(InputStream in, String relative, HttpServletResponse resp) throws IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType(ClasspathWebUiServlet.contentType(relative));
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("Cache-Control", ClasspathWebUiServlet.cacheControl(relative));
        try (OutputStream out = resp.getOutputStream()) {
            in.transferTo(out);
        }
    }
}
