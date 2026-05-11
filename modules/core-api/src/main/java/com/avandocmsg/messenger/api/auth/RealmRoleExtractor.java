package com.avandocmsg.messenger.api.auth;

import com.nimbusds.jwt.JWTClaimsSet;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Realm-роли из claim {@code realm_access} (Keycloak). */
public final class RealmRoleExtractor {

    private RealmRoleExtractor() {}

    @SuppressWarnings("unchecked")
    public static Set<String> realmRoles(JWTClaimsSet claims) {
        if (claims == null) {
            return Set.of();
        }
        Object ra = claims.getClaim("realm_access");
        if (!(ra instanceof Map)) {
            return Set.of();
        }
        var map = (Map<String, Object>) ra;
        Object rolesObj = map.get("roles");
        if (!(rolesObj instanceof List)) {
            return Set.of();
        }
        var list = (List<Object>) rolesObj;
        var out = new HashSet<String>();
        for (Object o : list) {
            if (o != null) {
                out.add(o.toString());
            }
        }
        return Set.copyOf(out);
    }
}
