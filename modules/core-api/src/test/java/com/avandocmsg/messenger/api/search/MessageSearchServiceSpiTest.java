package com.avandocmsg.messenger.api.search;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.testsupport.EmptyChatPersistencePort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageSearchServiceSpiTest {

    @Test
    void usesPrimaryBackendAndFallsBackToSqlBackendOnFailure() {
        var chatId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var primary = new StubBackend("solr", true, true, message("solr-hit", chatId, userId));
        var fallback = new StubBackend("sql", true, false, message("sql-hit", chatId, userId));
        var service = new MessageSearchService(
            new Chats(chatId),
            new SearchBackendBinding(primary, fallback)
        );

        var results = service.search(userId, "hello", 10);

        assertEquals(List.of("sql-hit"), results.stream().map(MessageResponse::id).toList());
        assertEquals(1, primary.calls);
        assertEquals(1, fallback.calls);
    }

    @Test
    void blankQueryDoesNotTouchBackends() {
        var primary = new StubBackend("solr", true, false);
        var fallback = new StubBackend("sql", true, false);
        var service = new MessageSearchService(
            new Chats(UUID.randomUUID()),
            new SearchBackendBinding(primary, fallback)
        );

        assertTrue(service.search(UUID.randomUUID(), "  ", 10).isEmpty());
        assertEquals(0, primary.calls);
        assertEquals(0, fallback.calls);
    }

    private static MessageResponse message(String id, UUID chatId, UUID senderId) {
        return new MessageResponse(
            id,
            chatId.toString(),
            senderId.toString(),
            "text",
            "hello",
            null,
            false,
            Instant.EPOCH,
            null,
            null,
            null
        );
    }

    private static final class Chats extends EmptyChatPersistencePort {
        private final UUID chatId;

        private Chats(UUID chatId) {
            this.chatId = chatId;
        }

        @Override
        public List<UUID> listChatIdsForUser(UUID userId) {
            return List.of(chatId);
        }
    }

    private static final class StubBackend implements MessageSearchBackend {
        private final String profileId;
        private final boolean enabled;
        private final boolean fail;
        private final List<MessageResponse> responses;
        private int calls;

        private StubBackend(String profileId, boolean enabled, boolean fail, MessageResponse... responses) {
            this.profileId = profileId;
            this.enabled = enabled;
            this.fail = fail;
            this.responses = List.of(responses);
        }

        @Override
        public String profileId() {
            return profileId;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public List<MessageResponse> search(UUID userId, List<UUID> chatIds, String query, int limit) throws Exception {
            calls++;
            if (fail) {
                throw new IllegalStateException("backend unavailable");
            }
            return responses;
        }
    }
}
