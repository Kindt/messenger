package com.avandocmsg.messenger.api.admin.ui;

import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminConsoleRedirectResourceTest {

    @Test
    void webConsoleLocation_stripsApiBasePath() {
        URI base = URI.create("http://localhost:8080/api/");
        URI loc = UriBuilder.fromUri(base).replacePath("/admin/").build();
        assertEquals("http://localhost:8080/admin/", loc.toString());
    }

    @Test
    void webConsoleLocation_withoutTrailingSlashOnBase() {
        URI base = URI.create("http://localhost:8080/api");
        URI loc = UriBuilder.fromUri(base).replacePath("/admin/").build();
        assertEquals("http://localhost:8080/admin/", loc.toString());
    }
}
