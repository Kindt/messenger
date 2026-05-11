package com.avandocmsg.messenger.api.contacts;

import com.avandocmsg.messenger.api.contacts.dto.AddContactRequest;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContactResourceTest {

    @Test
    void remove_invalidContactId_throwsInvalidUuidParameterException() {
        var resource = new ContactResource(null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.remove("not-a-uuid", userSecurityContext()));
    }

    @Test
    void add_invalidBodyUserId_throwsInvalidUuidParameterException() {
        var resource = new ContactResource(null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.add(new AddContactRequest("also-not-uuid"), userSecurityContext()));
    }

    @Test
    void add_nullBody_returns400() {
        var resource = new ContactResource(null, I18nTestFixtures.messagesEn());
        var res = resource.add(null, userSecurityContext());
        assertEquals(400, res.getStatus());
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
