package com.avandocmsg.messenger.api.live;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveKitTokenServiceTest {

    @Test
    void enabled_falseWhenLiveKitEnvMissing() {
        var svc = new LiveKitTokenService(new AppConfig());
        assertFalse(svc.enabled());
    }

    @Test
    void createAccessToken_signsJwtWithVideoClaims() throws Exception {
        var cfg = new AppConfig() {
            @Override
            public String livekitUrl() {
                return "wss://livekit.example";
            }

            @Override
            public String livekitApiKey() {
                return "testkey";
            }

            @Override
            public String livekitApiSecret() {
                return "testsecret32bytesminimumlength!!";
            }
        };
        var svc = new LiveKitTokenService(cfg);
        assertTrue(svc.enabled());

        var token = svc.createAccessToken("room-1", "user-abc", true, 120);
        var jwt = SignedJWT.parse(token);
        assertEquals("testkey", jwt.getJWTClaimsSet().getIssuer());
        assertEquals("user-abc", jwt.getJWTClaimsSet().getSubject());
        @SuppressWarnings("unchecked")
        var video = (java.util.Map<String, Object>) jwt.getJWTClaimsSet().getClaim("video");
        assertNotNull(video);
        assertEquals(true, video.get("roomJoin"));
        assertEquals("room-1", video.get("room"));
        assertEquals(true, video.get("canPublish"));
        assertEquals(true, video.get("canSubscribe"));
    }
}
