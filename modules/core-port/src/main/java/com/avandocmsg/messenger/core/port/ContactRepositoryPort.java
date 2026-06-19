package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.Contact;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.List;
import java.util.UUID;

/** Outbound persistence for user contacts. */
public interface ContactRepositoryPort {
    List<Contact> list(UserId userId);

    boolean add(UserId userId, UserId contactUserId);

    boolean remove(UserId userId, UserId contactUserId);

    List<UUID> findByPhoneHashes(UserId userId, List<String> phoneHashes);
}
