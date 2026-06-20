package com.avandocmsg.messenger.api;

import com.avandocmsg.messenger.api.bootstrap.CoreApiComposition;
import com.avandocmsg.messenger.api.bootstrap.EmbeddedTomcatBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded entry point for core-api. WAR deploy uses {@link com.avandocmsg.messenger.api.bootstrap.CoreApiServletContextListener}.
 */
public class MessengerApplication {
    private static final Logger log = LoggerFactory.getLogger(MessengerApplication.class);

    private final EmbeddedTomcatBootstrap bootstrap;

    public MessengerApplication() {
        this(new CoreApiComposition());
    }

    MessengerApplication(CoreApiComposition composition) {
        this.bootstrap = new EmbeddedTomcatBootstrap(composition);
    }

    public void start() throws Exception {
        bootstrap.start();
    }

    public void stop() throws Exception {
        bootstrap.stop();
    }

    public static void main(String[] args) throws Exception {
        var app = new MessengerApplication();
        app.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                app.stop();
            } catch (Exception e) {
                log.warn("Shutdown error", e);
            }
        }));
        Thread.currentThread().join();
    }
}
