package com.avandocmsg.messenger.api.bootstrap;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WAR bootstrap: wires Jersey and background services when deployed to an external servlet container.
 */
public class CoreApiServletContextListener implements ServletContextListener {
    public static final String COMPOSITION_ATTR = "com.avandocmsg.messenger.api.bootstrap.CoreApiComposition";

    private static final Logger log = LoggerFactory.getLogger(CoreApiServletContextListener.class);

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            var composition = new CoreApiComposition();
            event.getServletContext().setAttribute(COMPOSITION_ATTR, composition);
            composition.wireToServletContext(event.getServletContext());
            composition.startBackgroundServices();
            log.info(
                "core-api WAR context initialized (API locale: {})",
                composition.getAppConfig().locale().toLanguageTag()
            );
        } catch (Exception e) {
            throw new RuntimeException("core-api WAR bootstrap failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        var composition = event.getServletContext().getAttribute(COMPOSITION_ATTR);
        if (composition instanceof CoreApiComposition coreApiComposition) {
            try {
                coreApiComposition.stopBackgroundServices();
            } catch (Exception e) {
                log.warn("core-api WAR shutdown error", e);
            }
        }
        event.getServletContext().removeAttribute(COMPOSITION_ATTR);
    }
}
