package com.avandocmsg.messenger.api.blocks;

import com.avandocmsg.messenger.api.blocks.dto.BlockUserRequest;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BlocksResourceTest {

    private final UUID actorId = UUID.randomUUID();

    @Test
    void unblock_invalidPathUserId_throwsInvalidUuidParameterException() {
        var resource = new BlocksResource(null, null, null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.unblock("not-a-uuid", securityContext()));
    }

    @Test
    void block_invalidBodyUserId_throwsInvalidUuidParameterException() {
        var resource = new BlocksResource(null, null, null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.block(new BlockUserRequest("bad-uuid"), securityContext()));
    }

    private SecurityContext securityContext() {
        return new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return actorId::toString;
            }

            @Override
            public boolean isUserInRole(String role) {
                return false;
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public String getAuthenticationScheme() {
                return "Bearer";
            }
        };
    }
}
