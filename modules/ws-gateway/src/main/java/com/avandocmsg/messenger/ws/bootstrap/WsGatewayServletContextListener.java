package com.avandocmsg.messenger.ws.bootstrap;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WAR entry: starts {@link WsGatewayComposition} before Tomcat WebSocket SCI scans
 * {@link com.avandocmsg.messenger.ws.MessagingWebSocket}.
 */
public final class WsGatewayServletContextListener implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(WsGatewayServletContextListener.class);
    static final String COMPOSITION_ATTR = "com.avandocmsg.messenger.ws.composition";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            var composition = WsGatewayComposition.start();
            event.getServletContext().setAttribute(COMPOSITION_ATTR, composition);
            log.info("ws-gateway composition ready (WAR mode, /ws via container WebSocket SCI)");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start ws-gateway composition", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        var attr = event.getServletContext().getAttribute(COMPOSITION_ATTR);
        if (attr instanceof WsGatewayComposition composition) {
            composition.close();
            event.getServletContext().removeAttribute(COMPOSITION_ATTR);
        }
    }
}
