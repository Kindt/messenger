package com.avandocmsg.messenger.ws.bootstrap;

import com.avandocmsg.messenger.ws.MessagingWebSocket;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.apache.tomcat.websocket.server.WsSci;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Embedded Tomcat bootstrap with portable WebSocket SCI registration. */
public final class EmbeddedWsTomcatBootstrap {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedWsTomcatBootstrap.class);
    private static final String TEMP_PREFIX = "ws-gateway-docbase-";

    private EmbeddedWsTomcatBootstrap() {
    }

    public static void startAndAwait(int port) throws IOException {
        try {
            var tomcat = new Tomcat();
            tomcat.setPort(port);
            tomcat.getConnector().setProperty("bindOnInit", "false");

            Path docBase = createSecureTempDocBase();
            docBase.toFile().deleteOnExit();
            var ctx = tomcat.addWebapp("", docBase.toAbsolutePath().toString());
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
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to start ws-gateway Tomcat on port " + port, e);
        }
    }

    private static Path createSecureTempDocBase() throws IOException {
        try {
            var attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            return Files.createTempDirectory(TEMP_PREFIX, attr);
        } catch (UnsupportedOperationException posixUnsupported) {
            // Windows/non-POSIX: create then immediately lock down ACL (S5443 compliant pattern).
            Path dir = Files.createTempDirectory(TEMP_PREFIX); // NOSONAR java:S5443 — restrictOwnerOnly applied before return
            restrictOwnerOnly(dir);
            return dir;
        }
    }

    private static void restrictOwnerOnly(Path dir) throws IOException {
        AclFileAttributeView aclView = Files.getFileAttributeView(dir, AclFileAttributeView.class);
        if (aclView != null) {
            UserPrincipal owner = aclView.getOwner();
            AclEntry entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build();
            aclView.setAcl(List.of(entry));
            return;
        }
        var f = dir.toFile();
        if (!(f.setReadable(false, false) && f.setWritable(false, false) && f.setExecutable(false, false)
            && f.setReadable(true, true) && f.setWritable(true, true) && f.setExecutable(true, true))) {
            log.warn("Unable to fully restrict temp docBase permissions on {}", dir);
        }
    }
}
