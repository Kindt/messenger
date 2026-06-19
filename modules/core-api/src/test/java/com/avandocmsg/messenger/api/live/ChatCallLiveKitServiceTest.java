package com.avandocmsg.messenger.api.live;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatPersistenceAdapter;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCallLiveKitServiceTest {

    private static final UUID CHAT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID USER = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    private ChatCallLiveKitService service;
    private StubChatRepository chatRepo;

    @BeforeEach
    void setUp() {
        var appConfig = new AppConfig();
        var tokenService = new LiveKitTokenService(appConfig);
        chatRepo = new StubChatRepository();
        service = new ChatCallLiveKitService(new JdbcChatPersistenceAdapter(chatRepo), tokenService);
    }

    @Test
    void join_returnsEmptyWhenNotMember() {
        chatRepo.role = null;
        assertTrue(service.join(CHAT, USER).isEmpty());
    }

    @Test
    void join_returnsEmptyWhenLiveKitDisabled() {
        chatRepo.role = "member";
        var disabled = new ChatCallLiveKitService(new JdbcChatPersistenceAdapter(chatRepo), new LiveKitTokenService(new AppConfig()) {
            @Override
            public boolean enabled() {
                return false;
            }
        });
        assertTrue(disabled.join(CHAT, USER).isEmpty());
    }

    @Test
    void groupCallSfuEnabled_followsTokenService() {
        assertFalse(service.groupCallSfuEnabled());
    }

    static final class StubChatRepository extends ChatRepository {
        String role;

        StubChatRepository() {
            super(null, java.time.Clock.systemUTC(), com.avandocmsg.messenger.core.port.UuidGenerator.standard());
        }

        @Override
        public String getMemberRole(UUID chatId, UUID userId) {
            return role;
        }
    }
}
