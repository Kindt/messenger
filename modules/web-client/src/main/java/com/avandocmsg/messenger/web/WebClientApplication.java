package com.avandocmsg.messenger.web;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Автономный веб-клиент: встроенный Tomcat, статика из classpath, HTTP-прокси на core-api.
 * WebSocket к ws-gateway — {@code WEB_CLIENT_WS_PUBLIC_URL}; ICE для WebRTC — {@code WEB_CLIENT_RTC_ICE_SERVERS} (см. {@link WebClientEnvServlet}).
 */
public final class WebClientApplication {
    private static final Logger log = LoggerFactory.getLogger(WebClientApplication.class);
    private static final String DEFAULT_PORT = "9080";
    private static final String DEFAULT_API_UPSTREAM = "http://127.0.0.1:8080";
    private static final String SERVLET_NAME_WEB_UI = "webUi";

    public static void main(String[] args) throws Exception {
        int port = readPortFromEnv();
        String apiUpstream = readApiUpstreamFromEnv();

        var tomcat = new Tomcat();
        tomcat.setPort(port);
        var connector = tomcat.getConnector();
        connector.setProperty("bindOnInit", "false");

        Context ctx = tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.setParentClassLoader(WebClientApplication.class.getClassLoader());

        var apiProxy = Tomcat.addServlet(ctx, "apiProxy", new UpstreamProxyServlet());
        apiProxy.addInitParameter("upstreamBase", apiUpstream);
        ctx.addServletMappingDecoded("/api/*", "apiProxy");

        Tomcat.addServlet(ctx, "health", new HealthServlet());
        ctx.addServletMappingDecoded("/health", "health");

        Tomcat.addServlet(ctx, "nginxLbHealth", new NginxLbHealthServlet());
        ctx.addServletMappingDecoded("/nginx-health", "nginxLbHealth");

        Tomcat.addServlet(ctx, "webClientEnv", new WebClientEnvServlet());
        ctx.addServletMappingDecoded("/web-client-env.js", "webClientEnv");

        Path overlayRoot = readOverlayDirFromEnv();
        if (overlayRoot != null) {
            if (!Files.isDirectory(overlayRoot)) {
                throw new IllegalStateException("WEB_CLIENT_WEBUI_OVERLAY is not a directory: " + overlayRoot);
            }
            Tomcat.addServlet(ctx, SERVLET_NAME_WEB_UI, new OverlayWebUiServlet(overlayRoot));
            log.info("web-client webui overlay: {}", overlayRoot.toAbsolutePath());
        } else {
            Tomcat.addServlet(ctx, SERVLET_NAME_WEB_UI, new ClasspathWebUiServlet());
        }
        ctx.addServletMappingDecoded("/*", SERVLET_NAME_WEB_UI);

        tomcat.start();
        log.info("web-client started on port {} (API upstream: {})", port, apiUpstream);
        tomcat.getServer().await();
    }

    private static String stripTrailingSlashes(String s) {
        String r = s;
        while (r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    private static int readPortFromEnv() {
        return Integer.parseInt(System.getenv().getOrDefault("WEB_CLIENT_PORT", DEFAULT_PORT));
    }

    private static String readApiUpstreamFromEnv() {
        return stripTrailingSlashes(System.getenv().getOrDefault("WEB_CLIENT_API_UPSTREAM", DEFAULT_API_UPSTREAM));
    }

    private static Path readOverlayDirFromEnv() {
        String overlayDir = System.getenv("WEB_CLIENT_WEBUI_OVERLAY");
        if (overlayDir == null || overlayDir.isBlank()) {
            return null;
        }
        return Path.of(overlayDir.trim());
    }

    private WebClientApplication() {
    }
}
