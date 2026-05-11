package com.avandocmsg.messenger.api.admin.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
