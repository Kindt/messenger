package com.avandocmsg.messenger.core.benchmark;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.application.ChatApplicationService;
import com.avandocmsg.messenger.core.application.UserApplicationService;
import com.avandocmsg.messenger.core.domain.Chat;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.ChatType;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreApiBenchmarkTest {

    @Test
    void getChatForMember_1000Calls_underBudget() {
        var chatId = UUID.randomUUID();
        var viewerId = UUID.randomUUID();
        var port = (java.util.function.Function<ChatId, Optional<Chat>>) id ->
            Optional.of(new Chat(id, "Bench", ChatType.P2P, Instant.parse("2026-01-01T00:00:00Z")));
        var legacy = new ChatRepository(null, Clock.systemUTC(), UuidGenerator.standard()) {
            @Override
            public String getMemberRole(UUID c, UUID u) {
                return "member";
            }
        };
        var service = new ChatApplicationService(port::apply, legacy);

        var start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            assertTrue(service.getChatForMember(ChatId.of(chatId), UserId.of(viewerId)).isPresent());
        }
        var elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 500, "1000 getChatForMember calls took " + elapsedMs + "ms");
    }

    @Test
    void getProfileForViewer_1000Calls_underBudget() {
        var viewerId = UUID.randomUUID();
        UserRepositoryPort port = id -> Optional.of(new UserProfile(
            id,
            "bench",
            "Bench User",
            null,
            false,
            Instant.parse("2026-01-01T00:00:00Z"),
            "online",
            null,
            null,
            false));
        var service = new UserApplicationService(port);

        var start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            assertTrue(service.getProfileForViewer(UserId.of(viewerId), UserId.of(viewerId)).isPresent());
        }
        var elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 500, "1000 getProfileForViewer calls took " + elapsedMs + "ms");
    }
}
