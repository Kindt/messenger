package com.avandocmsg.messenger.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OverlayWebUiServletTest {

    @TempDir
    Path temp;

    @Test
    void servesOverlayFileWhenPresent() throws Exception {
        Path overlay = temp.resolve("webui-root");
        Files.createDirectories(overlay);
        Files.writeString(overlay.resolve("app.js"), "overlay-content", StandardCharsets.UTF_8);

        var servlet = new OverlayWebUiServlet(overlay);
        var req = mock(HttpServletRequest.class);
        var resp = mock(HttpServletResponse.class);
        when(req.getRequestURI()).thenReturn("/app.js");
        when(req.getContextPath()).thenReturn("");
        var out = new ByteArrayOutputStream();
        when(resp.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {
                // Servlet 3.1 async write listener unused in this unit-test stub
            }

            @Override
            public void write(int b) throws IOException {
                out.write(b);
            }
        });

        servlet.doGet(req, resp);

        assertEquals("overlay-content", out.toString(StandardCharsets.UTF_8));
        verify(resp).setContentType("application/javascript;charset=UTF-8");
    }

    @Test
    void fallsBackToClasspathWhenOverlayMissing() throws Exception {
        Path overlay = temp.resolve("empty-overlay");
        Files.createDirectories(overlay);

        var servlet = new OverlayWebUiServlet(overlay);
        var req = mock(HttpServletRequest.class);
        var resp = mock(HttpServletResponse.class);
        when(req.getRequestURI()).thenReturn("/index.html");
        when(req.getContextPath()).thenReturn("");
        var out = new ByteArrayOutputStream();
        when(resp.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {
                // Servlet 3.1 async write listener unused in this unit-test stub
            }

            @Override
            public void write(int b) throws IOException {
                out.write(b);
            }
        });

        servlet.doGet(req, resp);

        String body = out.toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("<!") || body.contains("html") || !body.isEmpty());
        verify(resp).setContentType("text/html;charset=UTF-8");
    }
}
