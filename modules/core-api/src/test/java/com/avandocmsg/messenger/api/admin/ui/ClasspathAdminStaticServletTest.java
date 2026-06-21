package com.avandocmsg.messenger.api.admin.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasspathAdminStaticServletTest {

    @Test
    void relativeAssetPath_mapsRootToIndex() {
        assertEquals("index.html", ClasspathAdminStaticServlet.relativeAssetPath(null));
        assertEquals("index.html", ClasspathAdminStaticServlet.relativeAssetPath(""));
        assertEquals("index.html", ClasspathAdminStaticServlet.relativeAssetPath("/"));
    }

    @Test
    void relativeAssetPath_rejectsTraversal() {
        assertNull(ClasspathAdminStaticServlet.relativeAssetPath("/../x"));
    }

    @Test
    void relativeAssetPath_stripsLeadingSlash() {
        assertEquals("app.js", ClasspathAdminStaticServlet.relativeAssetPath("/app.js"));
    }

    @Test
    void contentType_css() {
        assertEquals("text/css;charset=UTF-8", ClasspathAdminStaticServlet.contentType("styles.css"));
    }

    @Test
    void cacheControl_htmlNoStore() {
        assertEquals("no-store, max-age=0", ClasspathAdminStaticServlet.cacheControl("index.html"));
    }

    @Test
    void cacheControl_jsPublic() {
        assertEquals("public, max-age=3600", ClasspathAdminStaticServlet.cacheControl("app.js"));
    }

    @Test
    void adminUiAssets_includeExternalStackPanel() throws Exception {
        var panels = resourceText("admin-ui/panels.js");
        var helpers = resourceText("admin-ui/ui-helpers.js");

        assertTrue(panels.contains("mountExternalStackStatus"));
        assertTrue(panels.contains("\"core-external-stack\": mountExternalStackStatus"));
        assertTrue(helpers.contains("s.id === \"core-external-stack\""));
        assertTrue(helpers.contains("\"core-external-stack\""));
        assertTrue(helpers.contains("desired/observed"));
        assertTrue(panels.contains("externalStackFilters"));
        assertTrue(panels.contains("external-stack-drilldown"));
        assertTrue(panels.contains("support-badge"));
        assertTrue(panels.contains("candidate/deferred"));
        assertTrue(panels.contains("/platform/external-stack/profiles"));
        assertTrue(panels.contains("promotion_evidence"));
        assertTrue(panels.contains("unsupported_modes"));
        assertTrue(panels.contains("externalStackCheckpointJson"));
        assertTrue(panels.contains("/platform/external-stack/preflight/checkpoint"));
        assertTrue(panels.contains("externalStackManifestsJson"));
        assertTrue(panels.contains("/platform/external-stack/preflight/manifests"));
    }

    private static String resourceText(String path) throws IOException {
        try (var in = ClasspathAdminStaticServletTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
