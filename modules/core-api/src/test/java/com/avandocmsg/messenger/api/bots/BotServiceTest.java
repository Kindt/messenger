package com.avandocmsg.messenger.api.bots;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatPersistenceAdapter;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.application.MessageApplicationService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotServiceTest {

    @Test
    void normalizeListenMode_defaultsToMentionsOnly() {
        assertEquals("MENTIONS_ONLY", BotService.normalizeListenMode(null));
        assertEquals("MENTIONS_ONLY", BotService.normalizeListenMode(""));
    }

    @Test
    void normalizeListenMode_acceptsReadAll() {
        assertEquals("READ_ALL", BotService.normalizeListenMode("read_all"));
    }

    @Test
    void normalizeListenMode_rejectsUnknown() {
        assertNull(BotService.normalizeListenMode("ALL"));
    }

    @Test
    void isHttpsUrl_requiresHttpsAndHost() {
        assertTrue(BotService.isHttpsUrl("https://hooks.example.com/bot"));
        assertFalse(BotService.isHttpsUrl("http://hooks.example.com/bot"));
        assertFalse(BotService.isHttpsUrl("not-a-url"));
    }

    @Test
    void deleteMessage_delegatesToMessageApplicationService() {
        var chatId = UUID.randomUUID();
        var msgId = UUID.randomUUID();
        var botId = UUID.randomUUID();
        var messagePort = new DeleteStubMessagePort(chatId, msgId, botId);
        var chatRepo = new ChatRepository(null, java.time.Clock.systemUTC(),
            com.avandocmsg.messenger.core.port.UuidGenerator.standard()) {
            @Override
            public String getMemberRole(UUID c, UUID u) {
                return "member";
            }
        };
        var memberChatPort = new com.avandocmsg.messenger.core.port.ChatRepositoryPort() {
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
        var deleteCoordinator = new com.avandocmsg.messenger.core.application.MessageDeleteCoordinator(
            messagePort, com.avandocmsg.messenger.core.port.NatsOutboundPort.noop());
        var messageApp = new MessageApplicationService(messagePort, memberChatPort, null, null, null, deleteCoordinator, null);
        var service = new BotService(null, new JdbcChatPersistenceAdapter(chatRepo), messageApp, null, null, null);
        assertTrue(service.deleteMessage(botId, chatId, msgId));
        assertTrue(messagePort.deleted);
    }

    @Test
    void pinMessage_requiresAdminRoleBeforeApplicationService() {
        var chatId = UUID.randomUUID();
        var msgId = UUID.randomUUID();
        var botId = UUID.randomUUID();
        var messagePort = new VisibleMessagePort(chatId, msgId, botId);
        var chatRepo = new ChatRepository(null, java.time.Clock.systemUTC(),
            com.avandocmsg.messenger.core.port.UuidGenerator.standard()) {
            @Override
            public String getMemberRole(UUID c, UUID u) {
                return "member";
            }
        };
        var memberChatPort = new com.avandocmsg.messenger.core.port.ChatRepositoryPort() {
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
        var pinCoordinator = new com.avandocmsg.messenger.core.application.MessagePinCoordinator(
            messagePort, com.avandocmsg.messenger.core.port.NatsOutboundPort.noop());
        var messageApp = new MessageApplicationService(
            messagePort, memberChatPort, null, null, null, null, null, pinCoordinator);
        var service = new BotService(null, new JdbcChatPersistenceAdapter(chatRepo), messageApp, null, null, null);
        assertFalse(service.pinMessage(botId, chatId, msgId));
        assertFalse(messagePort.pinAttempted);
    }

    private static final class VisibleMessagePort implements com.avandocmsg.messenger.core.port.MessageRepositoryPort {
        private final com.avandocmsg.messenger.core.domain.Message message;
        boolean pinAttempted;

        VisibleMessagePort(UUID chatId, UUID msgId, UUID senderId) {
            message = new com.avandocmsg.messenger.core.domain.Message(
                com.avandocmsg.messenger.core.domain.MessageId.of(msgId),
                com.avandocmsg.messenger.core.domain.ChatId.of(chatId),
                com.avandocmsg.messenger.core.domain.UserId.of(senderId),
                "text", "hi", null, null, false,
                java.time.Instant.parse("2026-01-01T00:00:00Z"), null, null, null);
        }

        @Override
        public java.util.Optional<com.avandocmsg.messenger.core.domain.Message> findById(
            com.avandocmsg.messenger.core.domain.MessageId id) {
            return java.util.Optional.of(message);
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
            pinAttempted = true;
            return false;
        }

        @Override
        public boolean unpinMessage(com.avandocmsg.messenger.core.domain.ChatId chatId,
                                    com.avandocmsg.messenger.core.domain.MessageId messageId) {
            return false;
        }
    }

    private static final class DeleteStubMessagePort
        implements com.avandocmsg.messenger.core.port.MessageRepositoryPort {
        private final com.avandocmsg.messenger.core.domain.Message message;
        boolean deleted;

        DeleteStubMessagePort(UUID chatId, UUID msgId, UUID senderId) {
            message = new com.avandocmsg.messenger.core.domain.Message(
                com.avandocmsg.messenger.core.domain.MessageId.of(msgId),
                com.avandocmsg.messenger.core.domain.ChatId.of(chatId),
                com.avandocmsg.messenger.core.domain.UserId.of(senderId),
                "text", "hi", null, null, false,
                java.time.Instant.parse("2026-01-01T00:00:00Z"), null, null, null);
        }

        @Override
        public java.util.Optional<com.avandocmsg.messenger.core.domain.Message> findById(
            com.avandocmsg.messenger.core.domain.MessageId id) {
            return java.util.Optional.of(message);
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
            deleted = true;
            return true;
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
}
