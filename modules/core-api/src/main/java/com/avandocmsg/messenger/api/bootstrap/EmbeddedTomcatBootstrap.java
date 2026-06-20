package com.avandocmsg.messenger.api.bootstrap;

import com.avandocmsg.messenger.api.config.HotReloadWatcher;
import jakarta.servlet.ServletContext;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Embedded Tomcat lifecycle for core-api (default dev/lab deploy mode).
 */
public final class EmbeddedTomcatBootstrap {
    private static final Logger log = LoggerFactory.getLogger(EmbeddedTomcatBootstrap.class);

    private final CoreApiComposition composition;
    private Tomcat tomcat;
    private Context ctx;
    private HotReloadWatcher watcher;

    public EmbeddedTomcatBootstrap(CoreApiComposition composition) {
        this.composition = composition;
    }

    public CoreApiComposition composition() {
        return composition;
    }

    public void start() throws Exception {
        tomcat = new Tomcat();
        tomcat.setPort(composition.getAppConfig().port());
        var connector = tomcat.getConnector();
        connector.setProperty("bindOnInit", "false");

        ctx = tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.setParentClassLoader(EmbeddedTomcatBootstrap.class.getClassLoader());

        composition.wireToServletContext((ServletContext) ctx);
        composition.startBackgroundServices();

        tomcat.start();
        log.info(
            "core-api started on port {} (API locale: {})",
            composition.getAppConfig().port(),
            composition.getAppConfig().locale().toLanguageTag()
        );

        if (composition.getAppConfig().hotReloadEnabled()) {
            var libDir = Paths.get(System.getProperty("app.home", "."), "lib");
            if (Files.exists(libDir)) {
                watcher = new HotReloadWatcher(libDir, this::restartContext);
                watcher.start();
            } else {
                log.info("HotReload disabled: {} not found", libDir);
            }
        }
    }

    private void restartContext() {
        try {
            log.info("Hot-reload triggered, restarting application context...");
            ctx.stop();
            ctx.destroy();
            composition.wireToServletContext((ServletContext) ctx);
            ctx.start();
            log.info("Application context reloaded successfully");
        } catch (Exception e) {
            log.error("Failed to reload application context", e);
        }
    }

    public void stop() throws Exception {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
        composition.stopBackgroundServices();
        if (tomcat != null) {
            tomcat.stop();
            tomcat = null;
        }
        ctx = null;
    }
}
