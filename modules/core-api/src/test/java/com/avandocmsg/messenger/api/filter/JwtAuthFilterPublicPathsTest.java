package com.avandocmsg.messenger.api.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthFilterPublicPathsTest {

    @Test
    void health_jerseyRelativePath_isPublic() {
        assertTrue(JwtAuthFilter.isPublicJerseyPath("v1/health"));
        assertTrue(JwtAuthFilter.isPublicJerseyPath("/v1/health"));
        assertTrue(JwtAuthFilter.isPublicJerseyPath("/api/v1/health"));
        assertTrue(JwtAuthFilter.isPublicJerseyPath("v1/health/ready"));
        assertTrue(JwtAuthFilter.isPublicJerseyPath("v1/health/live"));
    }

    @Test
    void authRefresh_isPublic() {
        assertTrue(JwtAuthFilter.isPublicJerseyPath("v1/auth/refresh"));
        assertTrue(JwtAuthFilter.isPublicJerseyPath("/api/v1/auth/refresh"));
    }

    @Test
    void authLoginOptions_isPublic() {
        assertTrue(JwtAuthFilter.isPublicJerseyPath("v1/auth/login-options"));
        assertTrue(JwtAuthFilter.isPublicJerseyPath("/api/v1/auth/login-options"));
    }

    @Test
    void brandingPublic_isPublic() {
        assertTrue(JwtAuthFilter.isPublicJerseyPath("v1/branding"));
        assertTrue(JwtAuthFilter.isPublicJerseyPath("/api/v1/branding"));
    }

    @Test
    void brandingMe_requiresAuth() {
        assertFalse(JwtAuthFilter.isPublicJerseyPath("v1/branding/me"));
    }

    @Test
    void brandingManifest_isPublic() {
        assertTrue(JwtAuthFilter.isPublicJerseyPath("v1/branding/manifest.webmanifest"));
    }

    @Test
    void brandingMeManifest_requiresAuth() {
        assertFalse(JwtAuthFilter.isPublicJerseyPath("v1/branding/me/manifest.webmanifest"));
    }

    @Test
    void protectedPath_requiresAuth() {
        assertFalse(JwtAuthFilter.isPublicJerseyPath("v1/chats"));
        assertFalse(JwtAuthFilter.isPublicJerseyPath("/api/v1/users/me"));
    }

    @Test
    void avatarResizeWithAvt_allowsAnonymousGet() {
        var fileId = "6856bae7-9023-4b30-a0d5-cfa51ba3b4bc";
        var path = "v1/files/" + fileId + "/resize";
        assertTrue(JwtAuthFilter.isAvatarResizeWithAvtToken(path, "GET", "signed.token"));
        assertTrue(JwtAuthFilter.allowsAnonymousAccess(path, "GET", "signed.token"));
        assertFalse(JwtAuthFilter.isAvatarResizeWithAvtToken(path, "GET", null));
        assertFalse(JwtAuthFilter.isAvatarResizeWithAvtToken(path, "GET", "  "));
        assertFalse(JwtAuthFilter.isAvatarResizeWithAvtToken(path, "POST", "signed.token"));
        assertFalse(JwtAuthFilter.allowsAnonymousAccess(path, "GET", null));
    }
}
