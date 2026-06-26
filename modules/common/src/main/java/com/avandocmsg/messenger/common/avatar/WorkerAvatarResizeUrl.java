package com.avandocmsg.messenger.common.avatar;

import java.util.UUID;

/** Worker-side signed avatar resize URL minting (spec 068 W7). */
public final class WorkerAvatarResizeUrl {

    private static final int DEFAULT_SIZE = 128;
    private static final int DEFAULT_TTL_SECONDS = 3600;

    private WorkerAvatarResizeUrl() {
    }

    public record Config(
        boolean avatarsEnabled,
        boolean resizeEnabled,
        String hmacSecret,
        String publicBaseUrl,
        int ttlSeconds
    ) {
        public static Config fromEnv() {
            var avatars = !"false".equalsIgnoreCase(trim(System.getenv("AVATARS_ENABLED")));
            var resize = envBool("FILE_RESIZE_ENABLED", true);
            var secret = trim(System.getenv("AVATAR_TOKEN_HMAC_SECRET"));
            var base = trim(System.getenv("API_PUBLIC_BASE_URL"));
            var ttl = parsePositive(System.getenv("AVATAR_TOKEN_TTL_SECONDS"), DEFAULT_TTL_SECONDS);
            return new Config(avatars, resize, secret, base, ttl);
        }

        public boolean canMint() {
            return avatarsEnabled && resizeEnabled && hmacSecret != null && !hmacSecret.isBlank();
        }
    }

    public static String resizePath(Config config, UUID viewerId, UUID fileId) {
        return resizePath(config, viewerId, fileId, DEFAULT_SIZE, DEFAULT_SIZE);
    }

    public static String resizePath(Config config, UUID viewerId, UUID fileId, int width, int height) {
        if (config == null || !config.canMint() || viewerId == null || fileId == null) {
            return null;
        }
        var w = clampDimension(width);
        var h = clampDimension(height);
        var exp = System.currentTimeMillis() / 1000L + config.ttlSeconds();
        var token = AvatarTokenMint.mint(config.hmacSecret(), viewerId, fileId, w, h, exp);
        return "/api/v1/files/" + fileId + "/resize?w=" + w + "&h=" + h + "&avt=" + token;
    }

    public static String absoluteUrl(Config config, UUID viewerId, UUID fileId) {
        var path = resizePath(config, viewerId, fileId);
        if (path == null) {
            return null;
        }
        var base = config.publicBaseUrl();
        if (base == null || base.isBlank()) {
            return path;
        }
        var trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + path;
    }

    public static int clampDimension(int value) {
        return Math.min(Math.max(value, 1), 512);
    }

    private static boolean envBool(String key, boolean defaultValue) {
        var raw = trim(System.getenv(key));
        if (raw == null) {
            return defaultValue;
        }
        return !"false".equalsIgnoreCase(raw);
    }

    private static int parsePositive(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String trim(String raw) {
        if (raw == null) {
            return null;
        }
        var t = raw.trim();
        return t.isEmpty() ? null : t;
    }
}
