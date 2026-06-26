package com.avandocmsg.messenger.api.live;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** LiveKit access token (JWT HS256) — spec 013 L2 POC. */
public class LiveKitTokenService {
    private static final Logger log = LoggerFactory.getLogger(LiveKitTokenService.class);

    private final AppConfig appConfig;

    public LiveKitTokenService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public boolean enabled() {
        return appConfig.liveStreamingEnabled();
    }

    public String livekitUrl() {
        return appConfig.livekitUrl();
    }

    public String livekitIngressUrl() {
        return appConfig.livekitIngressUrl();
    }

    public String createAccessToken(String roomName, String identity, boolean canPublish, int ttlSeconds) {
        return createVideoToken(roomName, identity, canPublish, true, false, ttlSeconds);
    }

    /** Token for LiveKit egress API (room composite recording). */
    public String createRoomRecordToken(String roomName, int ttlSeconds) {
        return createVideoToken(roomName, "egress-recorder", false, false, true, ttlSeconds);
    }

    private String createVideoToken(String roomName, String identity, boolean canPublish, boolean canSubscribe,
                                    boolean roomRecord, int ttlSeconds) {
        if (!enabled()) {
            throw new IllegalStateException("LiveKit not configured");
        }
        try {
            Map<String, Object> video = new LinkedHashMap<>();
            video.put("roomJoin", true);
            video.put("room", roomName);
            video.put("canPublish", canPublish);
            video.put("canSubscribe", canSubscribe);
            if (roomRecord) {
                video.put("roomRecord", true);
            }

            var now = System.currentTimeMillis();
            var claims = new JWTClaimsSet.Builder()
                .subject(identity)
                .issuer(appConfig.livekitApiKey())
                .issueTime(new Date(now))
                .notBeforeTime(new Date(now))
                .expirationTime(new Date(now + ttlSeconds * 1000L))
                .claim("video", video)
                .build();

            var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signed.sign(new MACSigner(appConfig.livekitApiSecret().getBytes(StandardCharsets.UTF_8)));
            return signed.serialize();
        } catch (Exception e) {
            log.error("LiveKit token sign failed for room {}", roomName, e);
            throw new IllegalStateException("LiveKit token failed", e);
        }
    }
}
