package com.avandocmsg.messenger.api.contacts;

import com.avandocmsg.messenger.api.contacts.dto.ContactResponse;
import com.avandocmsg.messenger.api.contacts.dto.ImportContactsResponse;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.BlockRepositoryPort;
import com.avandocmsg.messenger.api.repository.ContactRepository;
import com.avandocmsg.messenger.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class ContactService {
    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final BlockRepositoryPort blockRepositoryPort;

    public ContactService(ContactRepository contactRepository, UserRepository userRepository,
                          BlockRepositoryPort blockRepositoryPort) {
        this.contactRepository = contactRepository;
        this.userRepository = userRepository;
        this.blockRepositoryPort = blockRepositoryPort;
    }

    public List<ContactResponse> list(UUID userId) {
        return contactRepository.list(userId);
    }

    public boolean add(UUID userId, UUID contactUserId) {
        if (userId.equals(contactUserId)) {
            return false;
        }
        if (userRepository.findById(contactUserId).isEmpty()) {
            return false;
        }
        var user = UserId.of(userId);
        var contact = UserId.of(contactUserId);
        if (blockRepositoryPort.exists(user, contact) || blockRepositoryPort.exists(contact, user)) {
            return false;
        }
        return contactRepository.add(userId, contactUserId);
    }

    public boolean remove(UUID userId, UUID contactUserId) {
        return contactRepository.remove(userId, contactUserId);
    }

    public ImportContactsResponse importByPhoneHashes(UUID userId, List<String> phoneHashes) {
        var foundIds = contactRepository.findByPhoneHashes(userId, phoneHashes);
        var contacts = foundIds.stream()
            .map(id -> {
                contactRepository.add(userId, id);
                return userRepository.findById(id).orElse(null);
            })
            .filter(u -> u != null)
            .map(u -> new ContactResponse(u.id(), u.username(), u.displayName(), u.phone(), null))
            .toList();
        return new ImportContactsResponse(contacts);
    }
}
