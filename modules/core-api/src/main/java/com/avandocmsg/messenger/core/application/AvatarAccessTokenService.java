package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.common.avatar.AvatarTokenMint;
import com.avandocmsg.messenger.common.avatar.AvatarTokenVerifier;

import java.time.Instant;

/** Mints and verifies signed avatar resize tokens (`avt`). */
public final class AvatarAccessTokenService {

    public static final int DEFAULT_TTL_SECONDS = 3600;
    public static final int MAX_AVATAR_DIMENSION = 512;

    private final String currentSecret;
    private final String previousSecret;
    private final int ttlSeconds;

    public AvatarAccessTokenService(AppConfig appConfig) {
        this(appConfig.avatarTokenHmacSecret(), appConfig.avatarTokenHmacSecretPrevious(), DEFAULT_TTL_SECONDS);
    }

    public AvatarAccessTokenService(String currentSecret, String previousSecret, int ttlSeconds) {
        this.currentSecret = currentSecret;
        this.previousSecret = previousSecret;
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
    }

    public String mint(java.util.UUID viewerId, java.util.UUID fileId, int width, int height) {
        var w = clampDimension(width);
        var h = clampDimension(height);
        var exp = Instant.now().getEpochSecond() + ttlSeconds;
        return AvatarTokenMint.mint(currentSecret, viewerId, fileId, w, h, exp);
    }

    public java.util.Optional<AvatarTokenVerifier.ParsedToken> verify(String token) {
        return AvatarTokenVerifier.verify(token, currentSecret, previousSecret);
    }

    public static int clampDimension(int value) {
        return Math.min(Math.max(value, 1), MAX_AVATAR_DIMENSION);
    }
}
