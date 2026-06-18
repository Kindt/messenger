package com.avandocmsg.messenger.api.filter;

import com.avandocmsg.messenger.api.config.AppConfig;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

import java.security.Principal;
import java.util.Set;

/** Bearer token auth for SCIM endpoints ({@code SCIM_BEARER_TOKEN}). */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class ScimBearerAuthFilter implements ContainerRequestFilter {

    private final AppConfig appConfig;

    @Inject
    public ScimBearerAuthFilter(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        var path = JwtAuthFilter.normalizeJerseyPath(request.getUriInfo().getPath());
        if (!path.startsWith("scim/v2/")) {
            return;
        }
        var expected = appConfig.scimBearerToken();
        if (expected.isEmpty()) {
            return;
        }
        var authHeader = request.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        var token = authHeader.substring(7).trim();
        if (!expected.get().equals(token)) {
            return;
        }
        var principal = new UserPrincipal("scim-provisioner", "scim-provisioner", Set.of("admin"));
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
                return "ScimBearer";
            }
        });
    }
}
