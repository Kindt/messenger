package com.avandocmsg.messenger.web;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Автономный веб-клиент: встроенный Tomcat, статика из classpath, HTTP-прокси на core-api.
 * WebSocket к ws-gateway задаётся через {@code WEB_CLIENT_WS_PUBLIC_URL} (см. {@link WebClientEnvServlet}).
 */
public final class WebClientApplication {
    private static final Logger log = LoggerFactory.getLogger(WebClientApplication.class);

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("WEB_CLIENT_PORT", "9080"));
        String apiUpstream = stripTrailingSlashes(
            System.getenv().getOrDefault("WEB_CLIENT_API_UPSTREAM", "http://127.0.0.1:8080"));

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

        Tomcat.addServlet(ctx, "webClientEnv", new WebClientEnvServlet());
        ctx.addServletMappingDecoded("/web-client-env.js", "webClientEnv");

        Tomcat.addServlet(ctx, "webUi", new ClasspathWebUiServlet());
        ctx.addServletMappingDecoded("/*", "webUi");

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

    private WebClientApplication() {
    }
}
