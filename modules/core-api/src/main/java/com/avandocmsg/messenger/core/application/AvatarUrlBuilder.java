package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.UUID;

/** Builds signed avatar resize URLs for API responses. */
public final class AvatarUrlBuilder {

    private static final int DEFAULT_SIZE = 128;

    private final AvatarAccessTokenService tokenService;
    private final AppConfig appConfig;

    public AvatarUrlBuilder(AvatarAccessTokenService tokenService, AppConfig appConfig) {
        this.tokenService = tokenService;
        this.appConfig = appConfig;
    }

    public String resizeUrl(UserId viewerId, FileId fileId) {
        return resizeUrl(viewerId, fileId, DEFAULT_SIZE, DEFAULT_SIZE);
    }

    public String resizeUrl(UserId viewerId, FileId fileId, int width, int height) {
        if (!appConfig.avatarsEnabled() || !appConfig.fileResizeEnabled()) {
            return null;
        }
        if (viewerId == null || fileId == null) {
            return null;
        }
        if (appConfig.avatarTokenHmacSecret().isBlank()) {
            return null;
        }
        var w = AvatarAccessTokenService.clampDimension(width);
        var h = AvatarAccessTokenService.clampDimension(height);
        var token = tokenService.mint(viewerId.value(), fileId.value(), w, h);
        return "/api/v1/files/" + fileId.value() + "/resize?w=" + w + "&h=" + h + "&avt=" + token;
    }

    public String resizeUrl(UserId viewerId, UUID fileId) {
        if (fileId == null) {
            return null;
        }
        return resizeUrl(viewerId, FileId.of(fileId));
    }
}
