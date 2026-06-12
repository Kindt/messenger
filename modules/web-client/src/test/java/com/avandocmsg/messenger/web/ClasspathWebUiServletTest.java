package com.avandocmsg.messenger.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClasspathWebUiServletTest {

    @Test
    void relativeAssetPath_root() {
        var req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/");
        when(req.getContextPath()).thenReturn("");
        assertEquals("index.html", ClasspathWebUiServlet.relativeAssetPath(req));
    }

    @Test
    void relativeAssetPath_withContext() {
        var req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/app/styles.css");
        when(req.getContextPath()).thenReturn("/app");
        assertEquals("styles.css", ClasspathWebUiServlet.relativeAssetPath(req));
    }

    @Test
    void relativeAssetPath_rejectsTraversal() {
        var req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/../x");
        when(req.getContextPath()).thenReturn("");
        assertNull(ClasspathWebUiServlet.relativeAssetPath(req));
    }

    @Test
    void relativeAssetPath_apiNotServed() {
        var req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/health");
        when(req.getContextPath()).thenReturn("");
        assertNull(ClasspathWebUiServlet.relativeAssetPath(req));
    }

    @Test
    void contentType_css() {
        assertEquals("text/css;charset=UTF-8", ClasspathWebUiServlet.contentType("styles.css"));
    }

    @Test
    void cacheControl_htmlNoStore() {
        assertEquals("no-store, max-age=0", ClasspathWebUiServlet.cacheControl("index.html"));
    }

    @Test
    void cacheControl_serviceWorkerNoStore() {
        assertEquals("no-store, max-age=0", ClasspathWebUiServlet.cacheControl("sw.js"));
    }

    @Test
    void cacheControl_appAndThemesNoStore() {
        assertEquals("no-store, max-age=0", ClasspathWebUiServlet.cacheControl("app.js"));
        assertEquals("no-store, max-age=0", ClasspathWebUiServlet.cacheControl("themes.css"));
        assertEquals("no-store, max-age=0", ClasspathWebUiServlet.cacheControl("styles.css"));
        assertEquals("no-store, max-age=0", ClasspathWebUiServlet.cacheControl("tailwind.css"));
    }
}
