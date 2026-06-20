package com.avandocmsg.messenger.ws.bootstrap;

import com.avandocmsg.messenger.ws.MessagingWebSocket;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.apache.tomcat.websocket.server.WsSci;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;

/** Embedded Tomcat bootstrap with portable WebSocket SCI registration. */
public final class EmbeddedWsTomcatBootstrap {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedWsTomcatBootstrap.class);

    private EmbeddedWsTomcatBootstrap() {
    }

    public static void startAndAwait(int port) throws Exception {
        var tomcat = new Tomcat();
        tomcat.setPort(port);
        var connector = tomcat.getConnector();
        connector.setProperty("bindOnInit", "false");

        File docBase = Files.createTempDirectory("ws-gateway-docbase").toFile();
        docBase.deleteOnExit();
        var ctx = tomcat.addWebapp("", docBase.getAbsolutePath());
        ctx.setParentClassLoader(EmbeddedWsTomcatBootstrap.class.getClassLoader());
        if (ctx instanceof StandardContext standardContext) {
            var jarScanner = new StandardJarScanner();
            jarScanner.setScanClassPath(true);
            standardContext.setJarScanner(jarScanner);
        }
        ctx.addServletContainerInitializer(new WsSci(), Set.of(MessagingWebSocket.class));

        tomcat.start();
        log.info("ws-gateway started on port {} (/ws)", port);
        tomcat.getServer().await();
    }
}
