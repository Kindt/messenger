package com.avandocmsg.messenger.api.files;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FileResourceTest {

    @Test
    void getInfo_invalidFileId_throwsInvalidUuidParameterException() {
        var resource = minimalResource();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.getInfo("not-a-uuid", userSecurityContext()));
    }

    @Test
    void revokePublicLink_invalidLinkId_throwsInvalidUuidParameterException() {
        var resource = minimalResource();
        var fileId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.revokePublicLink(fileId, "bad-link-id", userSecurityContext()));
    }

    @Test
    void revokePublicLink_invalidFileId_throwsInvalidUuidParameterException() {
        var resource = minimalResource();
        var linkId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.revokePublicLink("not-a-uuid", linkId, userSecurityContext()));
    }

    @Test
    void listPublicLinks_invalidFileId_throwsInvalidUuidParameterException() {
        var resource = minimalResource();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.listPublicLinks("not-a-uuid", userSecurityContext()));
    }

    @Test
    void resize_invalidFileId_throwsInvalidUuidParameterException() {
        var resource = minimalResource();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.resize("not-a-uuid", 200, 200, null, userSecurityContext()));
    }

    private static FileResource minimalResource() {
        return new FileResource(null, null, null, null, new AppConfig(), null, null, null, null,
            I18nTestFixtures.messagesEn());
    }

    private static SecurityContext userSecurityContext() {
        var actorId = UUID.randomUUID().toString();
        var principal = new UserPrincipal(actorId, "user", Set.of());
        return new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return principal;
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
