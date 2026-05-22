package com.avandocmsg.messenger.api.chats;

import com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse;
import com.avandocmsg.messenger.api.chats.dto.ChatResponse;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceTest {

    private final StubChatRepository chatRepo = new StubChatRepository();
    private final StubBlockRepository blockRepo = new StubBlockRepository();
    private final ChatService chatService = new ChatService(chatRepo, blockRepo, null, null,
        NatsOutboundPort.noop(), Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC),
        UuidGenerator.standard());

    final UUID userA = UUID.randomUUID();
    final UUID userB = UUID.randomUUID();
    final UUID chatId = UUID.randomUUID();

    @Test
    void findOrCreateP2P_returnsExistingChat() {
        var existingChat = new ChatResponse(chatId.toString(), "", "p2p", null, 2, false, false, null, null);
        chatRepo.p2pChats.put(new StubChatRepository.P2PKey(userA, userB), chatId);
        chatRepo.chats.put(chatId, existingChat);

        var result = chatService.findOrCreateP2P(userA, userB);

        assertNotNull(result);
        assertEquals(chatId.toString(), result.id());
    }

    @Test
    void findOrCreateP2P_blocksSameUser() {
        assertNull(chatService.findOrCreateP2P(userA, userA));
    }

    @Test
    void findOrCreateP2P_blocksWhenBlockExists() {
        chatRepo.p2pChats.put(new StubChatRepository.P2PKey(userA, userB), null);
        blockRepo.blocks.add(userA.toString() + ":" + userB.toString());

        var result = chatService.findOrCreateP2P(userA, userB);
        assertNull(result);
    }

    @Test
    void findOrCreateP2P_createsNew() {
        var result = chatService.findOrCreateP2P(userA, userB);

        assertNotNull(result);
        assertEquals("p2p", result.type());
    }

    @Test
    void createGroup_failsWithoutTitle() {
        assertNull(chatService.createGroup(null, userA, null));
    }

    @Test
    void createGroup_succeeds() {
        chatRepo.nextChatId = chatId;
        var title = "Test Group";
        var result = chatService.createGroup(title, userA, List.of(userB.toString()));

        assertNotNull(result);
        assertEquals(title, result.title());
    }

    @Test
    void addMember_deniedWhenNotAdmin() {
        chatRepo.roles.put(new StubChatRepository.RoleKey(chatId, userA), "member");

        assertFalse(chatService.addMember(chatId, userA, userB));
    }

    @Test
    void addMember_allowedForAdmin() {
        chatRepo.roles.put(new StubChatRepository.RoleKey(chatId, userA), "admin");

        assertTrue(chatService.addMember(chatId, userA, userB));
    }

    @Test
    void addMember_deniedWhenBlocked() {
        chatRepo.roles.put(new StubChatRepository.RoleKey(chatId, userA), "admin");
        blockRepo.blocks.add(userB.toString() + ":" + userA.toString());

        assertFalse(chatService.addMember(chatId, userA, userB));
    }

    @Test
    void removeMember_ownerCanRemove() {
        chatRepo.roles.put(new StubChatRepository.RoleKey(chatId, userA), "owner");
        chatRepo.roles.put(new StubChatRepository.RoleKey(chatId, userB), "member");

        assertTrue(chatService.removeMember(chatId, userA, userB));
    }

    @Test
    void removeMember_memberCannotRemove() {
        chatRepo.roles.put(new StubChatRepository.RoleKey(chatId, userA), "member");
        chatRepo.roles.put(new StubChatRepository.RoleKey(chatId, userB), "member");

        assertFalse(chatService.removeMember(chatId, userA, userB));
    }

    @Test
    void updateTitle_allowedForOwner() {
        chatRepo.roles.put(new StubChatRepository.RoleKey(chatId, userA), "owner");
        chatRepo.chats.put(chatId, new ChatResponse(chatId.toString(), "title", "group", userA.toString(), 1, false, false, null, null));

        assertTrue(chatService.updateTitle(chatId, userA, "new title"));
    }

    @Test
    void updateTitle_deniedForMember() {
        chatRepo.roles.put(new StubChatRepository.RoleKey(chatId, userA), "member");

        assertFalse(chatService.updateTitle(chatId, userA, "new title"));
    }

    @Test
    void listMembersForViewer_requiresMembership() {
        assertTrue(chatService.listMembersForViewer(chatId, userA).isEmpty());
    }

    @Test
    void listMembersForViewer_returnsListWhenMember() {
        chatRepo.roles.put(new StubChatRepository.RoleKey(chatId, userA), "member");
        chatRepo.membersList.put(chatId, List.of(new ChatMemberResponse(
            userB.toString(), "u", "d", "member", false, false, Instant.now())));

        var list = chatService.listMembersForViewer(chatId, userA).orElseThrow();
        assertEquals(1, list.size());
        assertEquals(userB.toString(), list.get(0).userId());
    }

    // --- Stub implementations ---

    static class StubChatRepository extends com.avandocmsg.messenger.api.repository.ChatRepository {
        final Map<P2PKey, UUID> p2pChats = new HashMap<>();
        final Map<UUID, ChatResponse> chats = new HashMap<>();
        final Map<RoleKey, String> roles = new HashMap<>();
        final Map<UUID, List<ChatMemberResponse>> membersList = new HashMap<>();
        UUID nextChatId = UUID.randomUUID();
        final Set<UUID> members = new HashSet<>();

        record P2PKey(UUID a, UUID b) {}
        record RoleKey(UUID chatId, UUID userId) {}

        StubChatRepository() {
            super(null, Clock.systemUTC(), UuidGenerator.standard());
        }

        @Override
        public Optional<UUID> findP2PChat(UUID u1, UUID u2) {
            var key = new P2PKey(u1, u2);
            var key2 = new P2PKey(u2, u1);
            if (p2pChats.containsKey(key)) return Optional.ofNullable(p2pChats.get(key));
            if (p2pChats.containsKey(key2)) return Optional.ofNullable(p2pChats.get(key2));
            return Optional.empty();
        }

        @Override
        public ChatResponse createP2P(UUID id, UUID u1, UUID u2) {
            var chat = new ChatResponse(id.toString(), "", "p2p", null, 2, false, false, null, null);
            chats.put(id, chat);
            return chat;
        }

        @Override
        public ChatResponse createGroup(UUID id, String title, UUID ownerId) {
            var chat = new ChatResponse(id.toString(), title, "group", ownerId.toString(), 1, false, false, null, null);
            chats.put(id, chat);
            return chat;
        }

        @Override
        public Optional<ChatResponse> findById(UUID id, UUID userId) {
            return Optional.ofNullable(chats.get(id));
        }

        @Override
        public String getMemberRole(UUID chatId, UUID userId) {
            return roles.get(new RoleKey(chatId, userId));
        }

        @Override
        public boolean addMember(UUID chatId, UUID userId, String role) {
            roles.put(new RoleKey(chatId, userId), role);
            members.add(userId);
            return true;
        }

        @Override
        public boolean removeMember(UUID chatId, UUID userId) {
            members.remove(userId);
            roles.remove(new RoleKey(chatId, userId));
            return true;
        }

        @Override
        public boolean updateTitle(UUID chatId, String title) {
            var chat = chats.get(chatId);
            if (chat != null) {
                chats.put(chatId, new ChatResponse(chat.id(), title, chat.type(), chat.ownerId(),
                    chat.memberCount(), chat.muted(), chat.personalFilterActive(),
                    chat.ttlSeconds(), chat.createdAt()));
                return true;
            }
            return false;
        }

        @Override
        public List<ChatResponse> listByUser(UUID userId) {
            return chats.values().stream().toList();
        }

        @Override
        public List<ChatMemberResponse> listMembers(UUID chatId) {
            return membersList.getOrDefault(chatId, List.of());
        }
    }

    static class StubBlockRepository extends com.avandocmsg.messenger.api.repository.BlockRepository {
        final Set<String> blocks = new HashSet<>();

        StubBlockRepository() {
            super(null);
        }

        @Override
        public boolean exists(UUID blockerId, UUID blockedId) {
            return blocks.contains(blockerId.toString() + ":" + blockedId.toString());
        }
    }
}
