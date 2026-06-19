package com.avandocmsg.messenger.api.contacts;

import com.avandocmsg.messenger.api.contacts.dto.ContactResponse;
import com.avandocmsg.messenger.api.contacts.dto.ImportContactsResponse;
import com.avandocmsg.messenger.core.port.UserLookupPort;
import com.avandocmsg.messenger.core.domain.Contact;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.BlockRepositoryPort;
import com.avandocmsg.messenger.core.port.ContactRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class ContactService {
    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private final ContactRepositoryPort contactRepositoryPort;
    private final UserLookupPort userLookupPort;
    private final BlockRepositoryPort blockRepositoryPort;

    public ContactService(ContactRepositoryPort contactRepositoryPort, UserLookupPort userLookupPort,
                          BlockRepositoryPort blockRepositoryPort) {
        this.contactRepositoryPort = contactRepositoryPort;
        this.userLookupPort = userLookupPort;
        this.blockRepositoryPort = blockRepositoryPort;
    }

    public List<ContactResponse> list(UUID userId) {
        return contactRepositoryPort.list(UserId.of(userId)).stream()
            .map(ContactService::toResponse)
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
            .map(u -> new ContactResponse(u.id(), u.username(), u.displayName(), u.phone(), null))
            .toList();
        return new ImportContactsResponse(contacts);
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
