package com.avandocmsg.messenger.api.chats.bans;

import com.avandocmsg.messenger.api.chats.bans.dto.ChatBanResponse;
import com.avandocmsg.messenger.core.port.ChatBanPort;
import com.avandocmsg.messenger.testsupport.EmptyChatPersistencePort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ChatBanServiceTest {

    private final StubChatBanPort banRepo = new StubChatBanPort();
    private final StubChatRepository chatRepo = new StubChatRepository();
    private final ChatBanService banService = new ChatBanService(banRepo, chatRepo);

    final UUID chatId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final UUID adminId = UUID.randomUUID();
    final UUID memberId = UUID.randomUUID();
    final UUID targetId = UUID.randomUUID();
    final Instant now = Instant.now();

    @Test
    void banUser_deniedWhenNotMember() {
        var result = banService.banUser(chatId, ownerId, targetId, "spam");
        assertNull(result);
    }

    @Test
    void banUser_deniedForRegularMember() {
        chatRepo.roles.put(chatId + ":" + memberId, "member");

        var result = banService.banUser(chatId, memberId, targetId, "spam");
        assertNull(result);
    }

    @Test
    void banUser_allowedForOwner() {
        chatRepo.roles.put(chatId + ":" + ownerId, "owner");
        chatRepo.roles.put(chatId + ":" + targetId, "member");

        var result = banService.banUser(chatId, ownerId, targetId, "spam");

        assertNotNull(result);
        assertEquals(targetId.toString(), result.userId());
        assertEquals(ownerId.toString(), result.bannedBy());
        assertEquals("spam", result.reason());
    }

    @Test
    void banUser_allowedForAdmin() {
        chatRepo.roles.put(chatId + ":" + adminId, "admin");
        chatRepo.roles.put(chatId + ":" + targetId, "member");

        var result = banService.banUser(chatId, adminId, targetId, null);

        assertNotNull(result);
        assertEquals(targetId.toString(), result.userId());
    }

    @Test
    void banUser_deniedForSelfBan() {
        chatRepo.roles.put(chatId + ":" + ownerId, "owner");

        var result = banService.banUser(chatId, ownerId, ownerId, null);
        assertNull(result);
    }

    @Test
    void banUser_deniedForOwnerTarget() {
        chatRepo.roles.put(chatId + ":" + adminId, "admin");
        chatRepo.roles.put(chatId + ":" + ownerId, "owner");

        var result = banService.banUser(chatId, adminId, ownerId, null);
        assertNull(result);
    }

    @Test
    void unbanUser_deniedForMember() {
        chatRepo.roles.put(chatId + ":" + memberId, "member");

        assertFalse(banService.unbanUser(chatId, memberId, targetId));
    }

    @Test
    void unbanUser_allowedForOwner() {
        chatRepo.roles.put(chatId + ":" + ownerId, "owner");

        assertTrue(banService.unbanUser(chatId, ownerId, targetId));
    }

    @Test
    void listBansForViewer_allowedForOwner() {
        chatRepo.roles.put(chatId + ":" + ownerId, "owner");
        banRepo.bans.add(new ChatBanResponse(UUID.randomUUID().toString(), chatId.toString(),
            targetId.toString(), ownerId.toString(), "spam", now));

        var result = banService.listBansForViewer(chatId, ownerId).orElseThrow();
        assertEquals(1, result.size());
        assertEquals("spam", result.get(0).reason());
    }

    @Test
    void listBansForViewer_deniedForMember() {
        chatRepo.roles.put(chatId + ":" + memberId, "member");

        assertTrue(banService.listBansForViewer(chatId, memberId).isEmpty());
    }

    @Test
    void isBanned_returnsTrueWhenBanned() {
        banRepo.banned.put(chatId + ":" + targetId, true);

        assertTrue(banService.isBanned(chatId, targetId));
    }

    @Test
    void isBanned_returnsFalseWhenNotBanned() {
        assertFalse(banService.isBanned(chatId, targetId));
    }

    static class StubChatBanPort implements ChatBanPort {
        final List<ChatBanResponse> bans = new ArrayList<>();
        final Map<String, Boolean> banned = new HashMap<>();

        StubChatBanPort() {
        }

        @Override
        public ChatBanResponse ban(UUID chatId, UUID userId, UUID bannedBy, String reason) {
            var resp = new ChatBanResponse(UUID.randomUUID().toString(), chatId.toString(),
                userId.toString(), bannedBy.toString(), reason, Instant.now());
            bans.add(resp);
            return resp;
        }

        @Override
        public Optional<ChatBanResponse> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public boolean unban(UUID chatId, UUID userId) {
            banned.remove(chatId + ":" + userId);
            return true;
        }

        @Override
        public List<ChatBanResponse> findByChatId(UUID chatId) {
            return bans.stream().filter(b -> b.chatId().equals(chatId.toString())).toList();
        }

        @Override
        public boolean isBanned(UUID chatId, UUID userId) {
            return banned.getOrDefault(chatId + ":" + userId, false);
        }
    }

    static class StubChatRepository extends EmptyChatPersistencePort {
        final Map<String, String> roles = new HashMap<>();
        final Map<String, Boolean> banned = new HashMap<>();

        @Override
        public String getMemberRole(UUID chatId, UUID userId) {
            return roles.get(chatId + ":" + userId);
        }

        @Override
        public boolean setBanned(UUID chatId, UUID userId, boolean banned) {
            this.banned.put(chatId + ":" + userId, banned);
            return true;
        }
    }
}
