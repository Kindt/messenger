package com.avandocmsg.messenger.api.contacts;

import com.avandocmsg.messenger.api.contacts.dto.ContactResponse;
import com.avandocmsg.messenger.api.users.dto.UserProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ContactServiceTest {

    private final StubContactRepository contactRepo = new StubContactRepository();
    private final StubUserRepository userRepo = new StubUserRepository();
    private final StubBlockRepository blockRepo = new StubBlockRepository();
    private final ContactService contactService = new ContactService(contactRepo, userRepo, blockRepo);

    final UUID userId = UUID.randomUUID();
    final UUID contactId = UUID.randomUUID();
    final Instant now = Instant.now();

    @Test
    void list_returnsContacts() {
        contactRepo.contacts.computeIfAbsent(userId, k -> new ArrayList<>())
            .add(new ContactResponse(contactId.toString(), "john", "John", "+1234567890", now));

        var result = contactService.list(userId);
        assertEquals(1, result.size());
        assertEquals("john", result.get(0).username());
    }

    @Test
    void add_blocksSelfContact() {
        assertFalse(contactService.add(userId, userId));
    }

    @Test
    void add_blocksNonExistentUser() {
        assertFalse(contactService.add(userId, contactId));
    }

    @Test
    void add_succeedsForValidUser() {
        userRepo.users.put(contactId, new UserProfile(contactId.toString(), "john", "John", null, false, now, "offline", null, null, false, null));

        assertTrue(contactService.add(userId, contactId));
    }

    @Test
    void add_rejectedWhenEitherPartyBlocked() {
        userRepo.users.put(contactId, new UserProfile(contactId.toString(), "john", "John", null, false, now, "offline", null, null, false, null));
        blockRepo.blockedPairs.add(userId + ":" + contactId);

        assertFalse(contactService.add(userId, contactId));
    }

    @Test
    void remove_delegatesToRepository() {
        contactRepo.removable.add(contactId);

        assertTrue(contactService.remove(userId, contactId));
    }

    @Test
    void importByPhoneHashes_findsAndAdds() {
        var hashes = List.of("hash1", "hash2");
        contactRepo.foundByHash.put(userId, List.of(contactId));
        userRepo.users.put(contactId, new UserProfile(contactId.toString(), "john", "John", null, false, now, "offline", null, null, false, null));

        var result = contactService.importByPhoneHashes(userId, hashes);

        assertEquals(1, result.contacts().size());
        assertEquals("john", result.contacts().get(0).username());
    }

    static class StubBlockRepository extends com.avandocmsg.messenger.api.repository.BlockRepository {
        final java.util.Set<String> blockedPairs = new java.util.HashSet<>();

        StubBlockRepository() {
            super(null);
        }

        @Override
        public boolean exists(java.util.UUID blockerId, java.util.UUID blockedId) {
            return blockedPairs.contains(blockerId + ":" + blockedId)
                || blockedPairs.contains(blockedId + ":" + blockerId);
        }
    }

    static class StubContactRepository extends com.avandocmsg.messenger.api.repository.ContactRepository {
        final Map<UUID, List<ContactResponse>> contacts = new HashMap<>();
        final Map<UUID, List<UUID>> foundByHash = new HashMap<>();
        final Set<UUID> removable = new HashSet<>();

        StubContactRepository() { super(null); }

        @Override
        public List<ContactResponse> list(UUID userId) {
            return contacts.getOrDefault(userId, List.of());
        }

        @Override
        public boolean add(UUID userId, UUID contactUserId) {
            contacts.computeIfAbsent(userId, k -> new ArrayList<>())
                .add(new ContactResponse(contactUserId.toString(), "user", "User", null, null));
            return true;
        }

        @Override
        public boolean remove(UUID userId, UUID contactUserId) {
            return removable.contains(contactUserId);
        }

        @Override
        public List<UUID> findByPhoneHashes(UUID userId, List<String> phoneHashes) {
            return foundByHash.getOrDefault(userId, List.of());
        }
    }

    static class StubUserRepository extends com.avandocmsg.messenger.api.repository.UserRepository {
        final Map<UUID, UserProfile> users = new HashMap<>();

        StubUserRepository() { super(null); }

        @Override
        public Optional<UserProfile> findById(UUID id) {
            return Optional.ofNullable(users.get(id));
        }
    }
}
