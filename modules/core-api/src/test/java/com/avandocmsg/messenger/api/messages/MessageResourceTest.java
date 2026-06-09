package com.avandocmsg.messenger.api.messages;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.messages.dto.ForwardMessageRequest;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageResourceTest {

    @Test
    void send_invalidReplyToMsgId_throwsInvalidUuidParameterException() {
        var resource = new MessageResource(null, null, new AppConfig(), I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.send(chatId,
                new SendMessageRequest("text", "hello", "bad-uuid", null, null, null, null),
                userSecurityContext()));
    }

    @Test
    void send_invalidChatId_throwsInvalidUuidParameterException() {
        var resource = new MessageResource(null, null, new AppConfig(), I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.send("not-a-uuid",
                new SendMessageRequest("text", "hello", null, null, null, null, null),
                userSecurityContext()));
    }

    @Test
    void list_invalidBeforeQuery_throwsInvalidUuidParameterException() {
        var resource = new MessageResource(new AllowAllMessageService(), null, new AppConfig(), I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.list(chatId, 50, "not-a-uuid", userSecurityContext()));
    }

    @Test
    void getById_invalidMsgId_throwsInvalidUuidParameterException() {
        var resource = new MessageResource(null, null, new AppConfig(), I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.getById(chatId, "bad-msg-id", userSecurityContext()));
    }

    @Test
    void forward_invalidTargetChatId_throwsInvalidUuidParameterException() {
        var resource = new MessageResource(null, null, new AppConfig(), I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        var msgId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.forward(chatId, msgId, new ForwardMessageRequest("not-a-uuid"),
                userSecurityContext()));
    }

    /** Lets list() reach query-param validation for {@code before}. */
    private static final class AllowAllMessageService extends MessageService {
        AllowAllMessageService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public boolean canAccessChat(UUID chatId, UUID readerId) {
            return true;
        }
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
