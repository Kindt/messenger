package com.avandocmsg.messenger.api.auth;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RealmRoleExtractorTest {

    @Test
    void parsesRealmAccessRoles() throws Exception {
        var claims = new JWTClaimsSet.Builder()
            .claim("realm_access", Map.of("roles", List.of("admin", "offline_access")))
            .build();

        var roles = RealmRoleExtractor.realmRoles(claims);

        assertTrue(roles.contains("admin"));
        assertTrue(roles.contains("offline_access"));
        assertEquals(2, roles.size());
    }

    @Test
    void missingRealmAccess_emptySet() throws Exception {
        var claims = new JWTClaimsSet.Builder().subject("x").build();
        assertTrue(RealmRoleExtractor.realmRoles(claims).isEmpty());
    }
}
