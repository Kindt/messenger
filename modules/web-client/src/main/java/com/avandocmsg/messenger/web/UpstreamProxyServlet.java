package com.avandocmsg.messenger.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Проксирует HTTP на core-api (или иной upstream), сохраняя путь {@code /api/...}.
 */
public final class UpstreamProxyServlet extends HttpServlet {

    private static final List<String> HOP_BY_HOP_REQUEST = List.of(
        "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
        "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length"
    );

    private static final List<String> HOP_BY_HOP_RESPONSE = List.of(
        "connection", "keep-alive", "transfer-encoding", "trailer"
    );

    private String upstreamBase;
    private HttpClient httpClient;

    @Override
    public void init() throws ServletException {
        var raw = getServletConfig().getInitParameter("upstreamBase");
        if (raw == null || raw.isBlank()) {
            throw new ServletException("init-param upstreamBase is required");
        }
        upstreamBase = stripTrailingSlashes(raw);
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            forward(req, resp);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resp.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT);
        }
    }

    private void forward(HttpServletRequest req, HttpServletResponse resp) throws IOException, InterruptedException {
        String uri = req.getRequestURI();
        String q = req.getQueryString();
        String target = upstreamBase + uri + (q != null && !q.isEmpty() ? "?" + q : "");
        var method = req.getMethod().toUpperCase(Locale.ROOT);

        var rb = HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofMinutes(5));
        copyRequestHeaders(req, rb);

        HttpRequest.BodyPublisher body;
        if ("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method)) {
            body = HttpRequest.BodyPublishers.noBody();
        } else {
            body = HttpRequest.BodyPublishers.ofInputStream(() -> {
                try {
                    return req.getInputStream();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        rb.method(method, body);

        HttpResponse<InputStream> upstreamResp = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
        resp.setStatus(upstreamResp.statusCode());
        copyResponseHeaders(upstreamResp, resp);
        try (InputStream in = upstreamResp.body(); OutputStream out = resp.getOutputStream()) {
            if (in != null) {
                in.transferTo(out);
            }
        }
    }

    private static void copyRequestHeaders(HttpServletRequest req, HttpRequest.Builder rb) {
        var names = req.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (isHopByHop(name, HOP_BY_HOP_REQUEST)) {
                continue;
            }
            var vals = req.getHeaders(name);
            while (vals.hasMoreElements()) {
                rb.header(name, vals.nextElement());
            }
        }
    }

    private static void copyResponseHeaders(HttpResponse<?> upstreamResp, HttpServletResponse resp) {
        for (var e : upstreamResp.headers().map().entrySet()) {
            String name = e.getKey();
            if (isHopByHop(name, HOP_BY_HOP_RESPONSE)) {
                continue;
            }
            for (String v : e.getValue()) {
                resp.addHeader(name, v);
            }
        }
    }

    private static boolean isHopByHop(String headerName, List<String> hopByHop) {
        String lower = headerName.toLowerCase(Locale.ROOT);
        return hopByHop.contains(lower);
    }

    private static String stripTrailingSlashes(String s) {
        String r = s;
        while (r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }
}
