package com.avandocmsg.messenger.api.filter;

import com.avandocmsg.messenger.api.auth.RealmRoleExtractor;
import com.avandocmsg.messenger.api.auth.TokenValidator;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.ext.Provider;

import java.security.Principal;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class JwtAuthFilter implements ContainerRequestFilter {

    /**
     * Без Bearer. {@code /files/pub} — kind A/C (анонимная выдача по токену); {@code /files/auth-link} — kind B, JWT обязателен, не публичный.
     * {@code v1/admin/console} и {@code /api/v1/admin/console} — редирект на встроенную веб-консоль {@code /admin/}.
     */
    /** Paths relative to Jersey servlet mapping {@code /api/*} (see {@link #normalizeJerseyPath}). */
    private static final Set<String> PUBLIC_JERSEY_PREFIXES = Set.of(
        "v1/auth/login",
        "v1/auth/logout",
        "v1/auth/register",
        "v1/auth/refresh",
        "v1/health",
        "v1/media/capabilities",
        "v1/metrics",
        "v1/files/pub",
        "v1/admin/console",
        "v1/bot",
        "v1/integrations/outbound"
    );

    private final TokenValidator tokenValidator;
    private final UserMessageSource messages;

    @Inject
    public JwtAuthFilter(TokenValidator tokenValidator, UserMessageSource messages) {
        this.tokenValidator = tokenValidator;
        this.messages = messages;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        var path = request.getUriInfo().getPath();
        if (isPublic(path)) {
            return;
        }

        var authHeader = request.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            request.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ApiError(401, messages.get("error.jwt.missing_header")))
                .build());
            return;
        }

        var token = authHeader.substring(7);
        var claims = tokenValidator.validate(token);
        if (claims == null) {
            request.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ApiError(401, messages.get("error.jwt.invalid_token")))
                .build());
            return;
        }

        try {
            var userId = claims.getStringClaim("sub");
            var username = claims.getStringClaim("preferred_username");

            var realmRoles = RealmRoleExtractor.realmRoles(claims);
            var principal = new UserPrincipal(userId, username, realmRoles);

            request.setSecurityContext(new SecurityContext() {
                @Override
                public Principal getUserPrincipal() {
                    return principal;
                }

                @Override
                public boolean isUserInRole(String role) {
                    return principal.hasRealmRole(role);
                }

                @Override
                public boolean isSecure() {
                    return request.getUriInfo().getAbsolutePath().getScheme().equals("https");
                }

                @Override
                public String getAuthenticationScheme() {
                    return "Bearer";
                }
            });
        } catch (Exception e) {
            request.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ApiError(401, messages.get("error.jwt.claims_parse")))
                .build());
        }
    }

    static String normalizeJerseyPath(String path) {
        if (path == null) {
            return "";
        }
        var p = path.startsWith("/") ? path.substring(1) : path;
        if (p.startsWith("api/")) {
            p = p.substring(4);
        }
        return p;
    }

    static boolean isPublicJerseyPath(String path) {
        var p = normalizeJerseyPath(path);
        if (p.endsWith("openapi.json") || p.endsWith("openapi.yaml")) {
            return true;
        }
        return PUBLIC_JERSEY_PREFIXES.stream()
            .anyMatch(pub -> p.equals(pub) || p.startsWith(pub + "/"));
    }

    private boolean isPublic(String path) {
        return isPublicJerseyPath(path);
    }
}
