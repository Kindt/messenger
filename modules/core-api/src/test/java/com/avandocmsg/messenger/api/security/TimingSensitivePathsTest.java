package com.avandocmsg.messenger.api.security;

import com.avandocmsg.messenger.api.config.AppConfig;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimingSensitivePathsTest {

    @Test
    void respond_withoutNormalization_returnsActionResult() {
        var config = new AppConfig();
        var response = TimingSensitivePaths.respond(config, () -> Response.ok("ok").build());
        assertEquals(200, response.getStatus());
    }

    @Test
    void padAuthFailure_whenNormalizationOff_doesNotThrow() {
        TimingSensitivePaths.padAuthFailure(new AppConfig());
    }
}
