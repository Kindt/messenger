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
    }

    @Test
    void authRefresh_isPublic() {
        assertTrue(JwtAuthFilter.isPublicJerseyPath("v1/auth/refresh"));
        assertTrue(JwtAuthFilter.isPublicJerseyPath("/api/v1/auth/refresh"));
    }

    @Test
    void protectedPath_requiresAuth() {
        assertFalse(JwtAuthFilter.isPublicJerseyPath("v1/chats"));
        assertFalse(JwtAuthFilter.isPublicJerseyPath("/api/v1/users/me"));
    }
}
