package com.avandocmsg.messenger.api.contacts;

import com.avandocmsg.messenger.api.contacts.dto.ContactResponse;
import com.avandocmsg.messenger.api.contacts.dto.ImportContactsResponse;
import com.avandocmsg.messenger.core.port.UserLookupPort;
import com.avandocmsg.messenger.core.domain.Contact;
import com.avandocmsg.messenger.core.application.AvatarApplicationService;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.BlockRepositoryPort;
import com.avandocmsg.messenger.core.port.ContactRepositoryPort;

import java.util.List;
import java.util.UUID;

public class ContactService {
    private final ContactRepositoryPort contactRepositoryPort;
    private final UserLookupPort userLookupPort;
    private final BlockRepositoryPort blockRepositoryPort;
    private final AvatarApplicationService avatarApplicationService;

    public ContactService(ContactRepositoryPort contactRepositoryPort, UserLookupPort userLookupPort,
                          BlockRepositoryPort blockRepositoryPort) {
        this(contactRepositoryPort, userLookupPort, blockRepositoryPort, null);
    }

    public ContactService(ContactRepositoryPort contactRepositoryPort, UserLookupPort userLookupPort,
                          BlockRepositoryPort blockRepositoryPort,
                          AvatarApplicationService avatarApplicationService) {
        this.contactRepositoryPort = contactRepositoryPort;
        this.userLookupPort = userLookupPort;
        this.blockRepositoryPort = blockRepositoryPort;
        this.avatarApplicationService = avatarApplicationService;
    }

    public List<ContactResponse> list(UUID userId) {
        var viewerId = UserId.of(userId);
        return contactRepositoryPort.list(viewerId).stream()
            .map(c -> enrichContact(c, viewerId))
            .toList();
    }

    public boolean add(UUID userId, UUID contactUserId) {
        if (userId.equals(contactUserId)) {
            return false;
        }
        if (userLookupPort.findById(contactUserId).isEmpty()) {
            return false;
        }
        var user = UserId.of(userId);
        var contact = UserId.of(contactUserId);
        if (blockRepositoryPort.exists(user, contact) || blockRepositoryPort.exists(contact, user)) {
            return false;
        }
        return contactRepositoryPort.add(user, contact);
    }

    public boolean remove(UUID userId, UUID contactUserId) {
        return contactRepositoryPort.remove(UserId.of(userId), UserId.of(contactUserId));
    }

    public ImportContactsResponse importByPhoneHashes(UUID userId, List<String> phoneHashes) {
        var owner = UserId.of(userId);
        var foundIds = contactRepositoryPort.findByPhoneHashes(owner, phoneHashes);
        var contacts = foundIds.stream()
            .map(id -> {
                contactRepositoryPort.add(owner, UserId.of(id));
                return userLookupPort.findById(id).orElse(null);
            })
            .filter(u -> u != null)
            .map(u -> enrichContactResponse(owner, u))
            .toList();
        return new ImportContactsResponse(contacts);
    }

    private ContactResponse enrichContact(Contact contact, UserId viewerId) {
        var base = toResponse(contact);
        if (avatarApplicationService == null) {
            return base;
        }
        var avatarFileId = userLookupPort.findById(contact.contactUserId().value())
            .map(u -> u.avatarFileId())
            .filter(id -> id != null && !id.isBlank())
            .map(id -> FileId.of(java.util.UUID.fromString(id)))
            .orElse(null);
        return avatarApplicationService.enrichContactResponse(base, viewerId, avatarFileId);
    }

    private ContactResponse enrichContactResponse(UserId viewerId,
                                                  com.avandocmsg.messenger.api.users.dto.UserProfile u) {
        var base = new ContactResponse(u.id(), u.username(), u.displayName(), u.phone(), null);
        if (avatarApplicationService == null) {
            return base;
        }
        var avatarFileId = u.avatarFileId() != null && !u.avatarFileId().isBlank()
            ? FileId.of(java.util.UUID.fromString(u.avatarFileId())) : null;
        return avatarApplicationService.enrichContactResponse(base, viewerId, avatarFileId);
    }

    private static ContactResponse toResponse(Contact contact) {
        return new ContactResponse(
            contact.contactUserId().value().toString(),
            contact.username(),
            contact.displayName(),
            contact.phone(),
            contact.addedAt());
    }
}
