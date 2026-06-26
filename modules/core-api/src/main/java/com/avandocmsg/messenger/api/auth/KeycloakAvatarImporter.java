package com.avandocmsg.messenger.api.auth;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.application.AvatarApplicationService;
import com.avandocmsg.messenger.core.application.FileApplicationService;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Bounded Keycloak {@code picture} claim import (spec 068 W8). */
final class KeycloakAvatarImporter {
    private static final Logger log = LoggerFactory.getLogger(KeycloakAvatarImporter.class);

    private KeycloakAvatarImporter() {
    }

    static void maybeImport(String accessToken, AppConfig appConfig, UserRepositoryPort userRepositoryPort,
                            FileApplicationService fileApplicationService,
                            AvatarApplicationService avatarApplicationService, HttpClient httpClient) {
        if (!appConfig.avatarsEnabled() || !appConfig.keycloakAvatarImportEnabled()) {
            return;
        }
        if (fileApplicationService == null || avatarApplicationService == null) {
            return;
        }
        try {
            var claims = SignedJWT.parse(accessToken).getJWTClaimsSet();
            var sub = UserId.of(java.util.UUID.fromString(claims.getSubject()));
            var profile = userRepositoryPort.findById(sub).orElse(null);
            if (profile == null || profile.avatarFileId() != null) {
                return;
            }
            var picture = claims.getStringClaim("picture");
            if (picture == null || picture.isBlank()) {
                return;
            }
            var bytes = downloadBounded(httpClient, picture.trim(), appConfig.keycloakAvatarImportMaxBytes());
            if (bytes == null || bytes.length == 0) {
                return;
            }
            var mime = sniffImageMime(bytes);
            var ext = "jpg";
            if ("image/png".equals(mime)) {
                ext = "png";
            } else if ("image/webp".equals(mime)) {
                ext = "webp";
            }
            var uploaded = fileApplicationService.upload(
                new ByteArrayInputStream(bytes),
                "keycloak-avatar." + ext,
                mime,
                bytes.length,
                sub);
            uploaded.ifPresent(result -> avatarApplicationService.setUserAvatarFromImport(sub, result.file().id()));
        } catch (Exception e) {
            log.debug("keycloak avatar import skipped: {}", e.getMessage());
        }
    }

    private static byte[] downloadBounded(HttpClient httpClient, String url, int maxBytes) {
        if (maxBytes <= 0) {
            return null;
        }
        try {
            var req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "image/*")
                .GET()
                .build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return null;
            }
            var body = resp.body();
            if (body == null || body.length == 0 || body.length > maxBytes) {
                return null;
            }
            return body;
        } catch (Exception e) {
            return null;
        }
    }

    private static String sniffImageMime(byte[] bytes) {
        if (bytes.length >= 8
            && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 12
            && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
            && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
