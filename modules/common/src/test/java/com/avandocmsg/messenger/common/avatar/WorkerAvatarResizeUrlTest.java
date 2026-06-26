package com.avandocmsg.messenger.common.avatar;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkerAvatarResizeUrlTest {

    private static final UUID VIEWER = UUID.fromString("aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee");
    private static final UUID FILE = UUID.fromString("11111111-2222-4333-8444-555555555555");

    @Test
    void resizePath_includesAvtToken() {
        var cfg = new WorkerAvatarResizeUrl.Config(true, true, "test-secret", null, 3600);
        var path = WorkerAvatarResizeUrl.resizePath(cfg, VIEWER, FILE);
        assertNotNull(path);
        assertTrue(path.contains("/resize?"));
        assertTrue(path.contains("avt="));
    }

    @Test
    void absoluteUrl_prependsPublicBase() {
        var cfg = new WorkerAvatarResizeUrl.Config(true, true, "test-secret", "https://api.example.com", 3600);
        var url = WorkerAvatarResizeUrl.absoluteUrl(cfg, VIEWER, FILE);
        assertNotNull(url);
        assertTrue(url.startsWith("https://api.example.com/api/v1/files/"));
    }
}
