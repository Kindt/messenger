package com.avandocmsg.messenger.api.bootstrap;

import jakarta.servlet.ServletContextListener;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreApiBootstrapSmokeTest {

    @Test
    void servletContextListener_implementsJakartaContract() {
        assertTrue(ServletContextListener.class.isAssignableFrom(CoreApiServletContextListener.class));
    }

    @Test
    void compositionAttributeName_isStable() {
        assertEquals(
            "com.avandocmsg.messenger.api.bootstrap.CoreApiComposition",
            CoreApiServletContextListener.COMPOSITION_ATTR
        );
    }

    @Test
    void webXml_registersServletContextListener() throws Exception {
        var webXml = Path.of("src/main/webapp/WEB-INF/web.xml");
        var xml = Files.readString(webXml);
        assertTrue(xml.contains("CoreApiServletContextListener"));
        assertTrue(xml.contains("metadata-complete=\"false\""));
    }

    @Test
    void jerseyServletName_matchesCompositionConstant() {
        assertEquals("jersey", CoreApiComposition.SERVLET_NAME);
    }
}
