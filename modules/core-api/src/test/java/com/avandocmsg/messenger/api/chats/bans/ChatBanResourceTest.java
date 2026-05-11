package com.avandocmsg.messenger.api.chats.bans;

import com.avandocmsg.messenger.api.chats.bans.dto.ChatBanRequest;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatBanResourceTest {

    @Test
    void ban_invalidChatId_throwsInvalidUuidParameterException() {
        var resource = new ChatBanResource(null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.ban("not-a-uuid", new ChatBanRequest(UUID.randomUUID().toString(), null),
                ownerSecurityContext()));
    }

    @Test
    void ban_invalidBodyUserId_throwsInvalidUuidParameterException() {
        var resource = new ChatBanResource(null, I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.ban(chatId, new ChatBanRequest("bad-uuid", null), ownerSecurityContext()));
    }

    @Test
    void ban_nullBody_returns400() {
        var resource = new ChatBanResource(null, I18nTestFixtures.messagesEn());
        var res = resource.ban(UUID.randomUUID().toString(), null, ownerSecurityContext());
        assertEquals(400, res.getStatus());
    }

    private static SecurityContext ownerSecurityContext() {
        var actorId = UUID.randomUUID().toString();
        var principal = new UserPrincipal(actorId, "owner", Set.of("offline_access"));
        return new SecurityContext() {
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
                return false;
            }

            @Override
            public String getAuthenticationScheme() {
                return "Bearer";
            }
        };
    }
}
