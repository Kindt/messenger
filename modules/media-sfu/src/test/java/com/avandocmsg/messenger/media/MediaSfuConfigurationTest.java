package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MediaSfuConfigurationTest {

    @Test
    void usesResourceSavingEmbeddedDefaults() {
        var config = MediaSfuConfiguration.from(Map.of());

        assertEquals(MediaSfuMode.EMBEDDED, config.mode());
        assertEquals(Duration.ofMinutes(2), config.idleTimeout());
        assertEquals(4, config.lastN());
        assertEquals("127.0.0.1", config.publicAddress());
        assertEquals(40000, config.mediaPortMin());
    }

    @Test
    void readsStandaloneConfiguration() {
        var config = MediaSfuConfiguration.from(Map.of(
            "MEDIA_SFU_MODE", "standalone",
            "MEDIA_SFU_NODE_ID", "media-2",
            "MEDIA_SFU_PORT", "18090",
            "MEDIA_SFU_LAST_N", "8",
            "MEDIA_SFU_PUBLIC_ADDRESS", "203.0.113.10",
            "MEDIA_SFU_PORT_MIN", "41000",
            "MEDIA_SFU_PORT_MAX", "41099"
        ));

        assertEquals(MediaSfuMode.STANDALONE, config.mode());
        assertEquals("media-2", config.nodeId());
        assertEquals(18090, config.port());
        assertEquals(8, config.lastN());
        assertEquals("203.0.113.10", config.publicAddress());
        assertEquals(41000, config.mediaPortMin());
        assertEquals(41099, config.mediaPortMax());
    }

    @Test
    void rejectsUnsafeLimits() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MediaSfuConfiguration.from(Map.of("MEDIA_SFU_LAST_N", "1000"))
        );
    }
}
