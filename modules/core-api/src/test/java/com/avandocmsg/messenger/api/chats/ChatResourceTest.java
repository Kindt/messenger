package com.avandocmsg.messenger.api.chats;

import com.avandocmsg.messenger.api.chats.dto.CreateChatRequest;
import com.avandocmsg.messenger.api.chats.dto.MarkReadRequest;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatResourceTest {

    @Test
    void list_limitAboveMax_returns400() {
        var resource = new ChatResource(null, null, null, null, null, I18nTestFixtures.messagesEn());
        var response = resource.list(0, 1001, userSecurityContext());
        assertEquals(400, response.getStatus());
    }

    @Test
    void create_group_invalidMemberId_throwsInvalidUuidParameterException() {
        var resource = new ChatResource(null, null, null, null, null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.create(new CreateChatRequest("group", "My Group", List.of("not-a-uuid")),
                userSecurityContext()));
    }

    @Test
    void create_p2p_invalidMemberId_throwsInvalidUuidParameterException() {
        var resource = new ChatResource(null, null, null, null, null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.create(new CreateChatRequest("p2p", null, List.of("not-a-uuid")),
                userSecurityContext()));
    }

    @Test
    void markRead_invalidUpToMessageId_throwsInvalidUuidParameterException() {
        var resource = new ChatResource(null, null, null, null, null, I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.markRead(chatId, new MarkReadRequest("not-a-uuid"), userSecurityContext()));
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
