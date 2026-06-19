package com.avandocmsg.messenger.api.contacts;

import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.avandocmsg.messenger.core.domain.Contact;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcUserLookupAdapter;
import com.avandocmsg.messenger.core.port.ContactRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ContactServiceTest {

    private final StubContactPort contactRepo = new StubContactPort();
    private final StubUserRepository userRepo = new StubUserRepository();
    private final StubBlockPort blockRepo = new StubBlockPort();
    private final ContactService contactService = new ContactService(contactRepo, new JdbcUserLookupAdapter(userRepo), blockRepo);

    final UUID userId = UUID.randomUUID();
    final UUID contactId = UUID.randomUUID();
    final Instant now = Instant.now();

    @Test
    void list_returnsContacts() {
        contactRepo.contacts.computeIfAbsent(userId, k -> new ArrayList<>())
            .add(new Contact(UserId.of(contactId), "john", "John", "+1234567890", now));

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
        userRepo.users.put(contactId, new UserProfile(contactId.toString(), "john", "John", null, null, null, false, now, "offline", null, null, false, null, null, null));

        assertTrue(contactService.add(userId, contactId));
    }

    @Test
    void add_rejectedWhenEitherPartyBlocked() {
        userRepo.users.put(contactId, new UserProfile(contactId.toString(), "john", "John", null, null, null, false, now, "offline", null, null, false, null, null, null));
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
        userRepo.users.put(contactId, new UserProfile(contactId.toString(), "john", "John", null, null, null, false, now, "offline", null, null, false, null, null, null));

        var result = contactService.importByPhoneHashes(userId, hashes);

        assertEquals(1, result.contacts().size());
        assertEquals("john", result.contacts().get(0).username());
    }

    static class StubBlockPort implements com.avandocmsg.messenger.core.port.BlockRepositoryPort {
        final java.util.Set<String> blockedPairs = new java.util.HashSet<>();

        @Override
        public boolean exists(com.avandocmsg.messenger.core.domain.UserId blockerId,
                              com.avandocmsg.messenger.core.domain.UserId blockedId) {
            var a = blockerId.value().toString();
            var b = blockedId.value().toString();
            return blockedPairs.contains(a + ":" + b) || blockedPairs.contains(b + ":" + a);
        }

        @Override
        public boolean block(com.avandocmsg.messenger.core.domain.UserId blockerId,
                             com.avandocmsg.messenger.core.domain.UserId blockedId) {
            return false;
        }

        @Override
        public boolean unblock(com.avandocmsg.messenger.core.domain.UserId blockerId,
                               com.avandocmsg.messenger.core.domain.UserId blockedId) {
            return false;
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.core.domain.BlockedUser> listBlockedUsers(
            com.avandocmsg.messenger.core.domain.UserId blockerId) {
            return java.util.List.of();
        }
    }

    static class StubContactPort implements ContactRepositoryPort {
        final Map<UUID, List<Contact>> contacts = new HashMap<>();
        final Map<UUID, List<UUID>> foundByHash = new HashMap<>();
        final Set<UUID> removable = new HashSet<>();

        @Override
        public List<Contact> list(UserId userId) {
            return contacts.getOrDefault(userId.value(), List.of());
        }

        @Override
        public boolean add(UserId userId, UserId contactUserId) {
            contacts.computeIfAbsent(userId.value(), k -> new ArrayList<>())
                .add(new Contact(contactUserId, "user", "User", null, null));
            return true;
        }

        @Override
        public boolean remove(UserId userId, UserId contactUserId) {
            return removable.contains(contactUserId.value());
        }

        @Override
        public List<UUID> findByPhoneHashes(UserId userId, List<String> phoneHashes) {
            return foundByHash.getOrDefault(userId.value(), List.of());
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
