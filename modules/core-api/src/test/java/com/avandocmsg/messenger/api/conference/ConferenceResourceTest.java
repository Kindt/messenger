package com.avandocmsg.messenger.api.conference;

import com.avandocmsg.messenger.api.conference.dto.CreateConferenceRequest;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ConferenceResourceTest {

    @Test
    void create_invalidChatId_throwsInvalidUuidParameterException() {
        var resource = new ConferenceResource(null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.create("not-a-uuid", new CreateConferenceRequest(null), userSecurityContext()));
    }

    @Test
    void get_invalidConferenceId_throwsInvalidUuidParameterException() {
        var resource = new ConferenceResource(null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.get("not-a-uuid", userSecurityContext()));
    }

    @Test
    void list_invalidChatId_throwsInvalidUuidParameterException() {
        var resource = new ConferenceResource(null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.list("not-a-uuid", true, userSecurityContext()));
    }

    @Test
    void listParticipants_invalidConferenceId_throwsInvalidUuidParameterException() {
        var resource = new ConferenceResource(null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.listParticipants("not-a-uuid", userSecurityContext()));
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
