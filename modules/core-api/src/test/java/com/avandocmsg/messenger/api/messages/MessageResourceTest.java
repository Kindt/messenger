package com.avandocmsg.messenger.api.messages;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.messages.dto.ForwardMessageRequest;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.application.MessageApplicationService;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageResourceTest {

    @Test
    void send_invalidReplyToMsgId_throwsInvalidUuidParameterException() {
        var resource = new MessageResource(null, new AppConfig(), I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.send(chatId,
                new SendMessageRequest("text", "hello", "bad-uuid", null, null, null, null, null),
                userSecurityContext()));
    }

    @Test
    void send_invalidChatId_throwsInvalidUuidParameterException() {
        var resource = new MessageResource(null, new AppConfig(), I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.send("not-a-uuid",
                new SendMessageRequest("text", "hello", null, null, null, null, null, null),
                userSecurityContext()));
    }

    @Test
    void list_invalidBeforeQuery_throwsInvalidUuidParameterException() {
        var appService = new MessageApplicationService(new NoopMessagePort(), memberChatRepository());
        var resource = new MessageResource(appService, new AppConfig(), I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.list(chatId, 50, "not-a-uuid", null, userSecurityContext()));
    }

    @Test
    void getById_invalidMsgId_throwsInvalidUuidParameterException() {
        var resource = new MessageResource(null, new AppConfig(), I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.getById(chatId, "bad-msg-id", userSecurityContext()));
    }

    @Test
    void forward_invalidTargetChatId_throwsInvalidUuidParameterException() {
        var resource = new MessageResource(null, new AppConfig(), I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        var msgId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.forward(chatId, msgId, new ForwardMessageRequest("not-a-uuid"),
                userSecurityContext()));
    }

    /** Lets list() reach query-param validation for {@code before}. */
    private static ChatRepository memberChatRepository() {
        return new ChatRepository(null, java.time.Clock.systemUTC(), UuidGenerator.standard()) {
            @Override
            public String getMemberRole(java.util.UUID chatId, java.util.UUID userId) {
                return "member";
            }

            @Override
            public boolean isMemberBanned(java.util.UUID chatId, java.util.UUID userId) {
                return false;
            }
        };
    }

    private static final class NoopMessagePort implements MessageRepositoryPort {
        @Override
        public java.util.Optional<com.avandocmsg.messenger.core.domain.Message> findById(
            com.avandocmsg.messenger.core.domain.MessageId id) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<com.avandocmsg.messenger.core.domain.Message> insert(
            com.avandocmsg.messenger.core.port.MessageInsert command) {
            return java.util.Optional.empty();
        }

        @Override
        public boolean updateContent(com.avandocmsg.messenger.core.domain.MessageId id,
                                     com.avandocmsg.messenger.core.domain.UserId senderId, String content) {
            return false;
        }

        @Override
        public boolean softDelete(com.avandocmsg.messenger.core.domain.MessageId id) {
            return false;
        }

        @Override
        public boolean addReaction(com.avandocmsg.messenger.core.domain.MessageId messageId,
                                   com.avandocmsg.messenger.core.domain.UserId userId, String reaction) {
            return false;
        }

        @Override
        public boolean removeReaction(com.avandocmsg.messenger.core.domain.MessageId messageId,
                                      com.avandocmsg.messenger.core.domain.UserId userId, String reaction) {
            return false;
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
