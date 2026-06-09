package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.FilePublicLinkRepository;
import com.avandocmsg.messenger.core.domain.CreatedPublicLink;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.ResolvedPublicLink;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.PublicLinkPort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Delegates {@link PublicLinkPort} to legacy {@link FilePublicLinkRepository}. */
public final class FilePublicLinkPortAdapter implements PublicLinkPort {
    private final FilePublicLinkRepository delegate;

    public FilePublicLinkPortAdapter(FilePublicLinkRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public String sha256Hex(String utf8) {
        return FilePublicLinkRepository.sha256Hex(utf8);
    }

    @Override
    public Optional<CreatedPublicLink> createLink(FileId fileId, UserId createdBy, char kind,
                                                String passwordPlain, Instant expiresAt) {
        return delegate.insert(fileId.value(), createdBy.value(), kind, passwordPlain, expiresAt)
            .map(c -> new CreatedPublicLink(c.id(), c.rawToken(), c.expiresAt()));
    }

    @Override
    public boolean revokeLink(UserId ownerId, FileId fileId, UUID linkId) {
        return delegate.revoke(ownerId.value(), fileId.value(), linkId);
    }

    @Override
    public List<FilePublicLinkEntry> listByFileAndOwner(FileId fileId, UserId ownerId) {
        return delegate.listActiveByFileAndOwner(fileId.value(), ownerId.value()).stream()
            .map(s -> new FilePublicLinkEntry(s.id(), s.linkKind(), s.expiresAt(), s.createdAt()))
            .toList();
    }

    @Override
    public List<OwnerPublicLinkEntry> listByOwner(UserId ownerId, int limit) {
        return delegate.listActiveByOwner(ownerId.value(), limit).stream()
            .map(s -> new OwnerPublicLinkEntry(
                s.id(), s.fileId(), s.linkKind(), s.expiresAt(), s.createdAt(), s.filename()))
            .toList();
    }

    @Override
    public Optional<ResolvedPublicLink> findValidByTokenHash(String tokenHash) {
        return delegate.findValidByTokenHash(tokenHash)
            .map(r -> new ResolvedPublicLink(r.fileId(), r.linkKind(), r.createdBy(), r.passwordHash()));
    }
}
