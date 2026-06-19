package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.contacts.dto.ContactResponse;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcContactRepositoryAdapter;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.ContactRepositoryPort;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

/**
 * Legacy façade for contact JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcContactRepositoryAdapter}.
 */
public class ContactRepository {
    private final ContactRepositoryPort port;

    public ContactRepository(DataSource dataSource) {
        this.port = new JdbcContactRepositoryAdapter(dataSource);
    }

    ContactRepository(ContactRepositoryPort port) {
        this.port = port;
    }

    public List<ContactResponse> list(UUID userId) {
        return port.list(UserId.of(userId)).stream()
            .map(c -> new ContactResponse(
                c.contactUserId().value().toString(),
                c.username(),
                c.displayName(),
                c.phone(),
                c.addedAt()))
            .toList();
    }

    public boolean add(UUID userId, UUID contactUserId) {
        return port.add(UserId.of(userId), UserId.of(contactUserId));
    }

    public boolean remove(UUID userId, UUID contactUserId) {
        return port.remove(UserId.of(userId), UserId.of(contactUserId));
    }

    public List<UUID> findByPhoneHashes(UUID userId, List<String> phoneHashes) {
        return port.findByPhoneHashes(UserId.of(userId), phoneHashes);
    }
}
