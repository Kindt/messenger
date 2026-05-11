package com.avandocmsg.messenger.api.filter;

import java.security.Principal;
import java.util.Set;

/** {@link #userId()} совпадает с JWT {@code sub}; {@link #realmRoles()} — из {@code realm_access.roles} (Keycloak). */
public record UserPrincipal(String userId, String username, Set<String> realmRoles) implements Principal {
    public UserPrincipal(String userId, String username) {
        this(userId, username, Set.of());
    }

    @Override
    public String getName() {
        return userId;
    }

    public boolean hasRealmRole(String role) {
        return role != null && realmRoles.contains(role);
    }
}
