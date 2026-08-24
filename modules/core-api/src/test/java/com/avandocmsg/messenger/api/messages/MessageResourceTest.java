package com.avandocmsg.messenger.api.messages;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.messages.dto.ForwardMessageRequest;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;
import com.avandocmsg.messenger.core.application.MessageApplicationService;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageResourceTest {

    @Test
    void send_invalidReplyToMsgId_throwsInvalidUuidParameterException() {
        var resource = new MessageResource(null, new AppConfig(), null, I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.send(chatId,
                new SendMessageRequest("text", "hello", "bad-uuid", null, null, null, null, null, null),
                userSecurityContext()));
    }

    @Test
    void send_invalidChatId_throwsInvalidUuidParameterException() {
        var resource = new MessageResource(null, new AppConfig(), null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.send("not-a-uuid",
                new SendMessageRequest("text", "hello", null, null, null, null, null, null, null),
                userSecurityContext()));
    }

    @Test
    void list_limitAboveMax_returns400() {
        var appService = new MessageApplicationService(new NoopMessagePort(), memberChatPort());
        var resource = new MessageResource(appService, new AppConfig(), null, I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        var response = resource.list(chatId, 1001, null, null, userSecurityContext());
        assertEquals(400, response.getStatus());
    }

    @Test
    void list_invalidBeforeQuery_throwsInvalidUuidParameterException() {
        var appService = new MessageApplicationService(new NoopMessagePort(), memberChatPort());
        var resource = new MessageResource(appService, new AppConfig(), null, I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.list(chatId, 50, "not-a-uuid", null, userSecurityContext()));
    }

    @Test
    void getById_invalidMsgId_throwsInvalidUuidParameterException() {
        var resource = new MessageResource(null, new AppConfig(), null, I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.getById(chatId, "bad-msg-id", userSecurityContext()));
    }

    @Test
    void forward_invalidTargetChatId_throwsInvalidUuidParameterException() {
        var resource = new MessageResource(null, new AppConfig(), null, I18nTestFixtures.messagesEn());
        var chatId = UUID.randomUUID().toString();
        var msgId = UUID.randomUUID().toString();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.forward(chatId, msgId, new ForwardMessageRequest("not-a-uuid"),
                userSecurityContext()));
    }

    private static ChatRepositoryPort memberChatPort() {
        return new ChatRepositoryPort() {
            @Override
            public java.util.Optional<com.avandocmsg.messenger.core.domain.Chat> findById(
                com.avandocmsg.messenger.core.domain.ChatId id) {
                return java.util.Optional.empty();
            }

            @Override
            public boolean isMember(com.avandocmsg.messenger.core.domain.ChatId chatId,
                                    com.avandocmsg.messenger.core.domain.UserId userId) {
                return true;
            }

            @Override
            public java.util.Optional<String> memberRole(com.avandocmsg.messenger.core.domain.ChatId chatId,
                                                           com.avandocmsg.messenger.core.domain.UserId userId) {
                return java.util.Optional.of("member");
            }

            @Override
            public boolean isMemberBanned(com.avandocmsg.messenger.core.domain.ChatId chatId,
                                          com.avandocmsg.messenger.core.domain.UserId userId) {
                return false;
            }

            @Override
            public java.util.Optional<com.avandocmsg.messenger.core.domain.UserId> findOtherP2pMember(
                com.avandocmsg.messenger.core.domain.ChatId chatId,
                com.avandocmsg.messenger.core.domain.UserId userId) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<com.avandocmsg.messenger.core.domain.UserId> listMemberUserIds(
                com.avandocmsg.messenger.core.domain.ChatId chatId) {
                return java.util.List.of();
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
        public boolean existsClientMsgId(com.avandocmsg.messenger.core.domain.ChatId chatId,
                                         com.avandocmsg.messenger.core.domain.UserId senderId,
                                         String clientMsgId) {
            return false;
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

        @Override
        public boolean pinMessage(com.avandocmsg.messenger.core.domain.ChatId chatId,
                                  com.avandocmsg.messenger.core.domain.MessageId messageId,
                                  com.avandocmsg.messenger.core.domain.UserId pinnedBy) {
            return false;
        }

        @Override
        public boolean unpinMessage(com.avandocmsg.messenger.core.domain.ChatId chatId,
                                    com.avandocmsg.messenger.core.domain.MessageId messageId) {
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
