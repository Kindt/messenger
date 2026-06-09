package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.CreatedPublicLink;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.ResolvedPublicLink;
import com.avandocmsg.messenger.core.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port for file public link lifecycle (US3). */
public interface PublicLinkPort {
    String sha256Hex(String utf8);

    Optional<CreatedPublicLink> createLink(FileId fileId, UserId createdBy, char kind,
                                           String passwordPlain, Instant expiresAt);

    boolean revokeLink(UserId ownerId, FileId fileId, UUID linkId);

    List<FilePublicLinkEntry> listByFileAndOwner(FileId fileId, UserId ownerId);

    List<OwnerPublicLinkEntry> listByOwner(UserId ownerId, int limit);

    Optional<ResolvedPublicLink> findValidByTokenHash(String tokenHash);

    record FilePublicLinkEntry(String id, String linkKind, Instant expiresAt, Instant createdAt) {}

    record OwnerPublicLinkEntry(String id, String fileId, String linkKind, Instant expiresAt,
                                Instant createdAt, String filename) {}
}
