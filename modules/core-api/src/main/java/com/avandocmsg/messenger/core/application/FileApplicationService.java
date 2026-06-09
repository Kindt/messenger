package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.StoredFile;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.FileMetadataPort;

import java.util.Optional;

/** Hexagonal application service for file metadata reads (Phase 2d). */
public final class FileApplicationService {
    private final FileMetadataPort fileMetadataPort;
    private final MessageRepository legacyMessageRepository;

    public FileApplicationService(FileMetadataPort fileMetadataPort, MessageRepository legacyMessageRepository) {
        this.fileMetadataPort = fileMetadataPort;
        this.legacyMessageRepository = legacyMessageRepository;
    }

    public Optional<StoredFile> findById(FileId fileId) {
        return fileMetadataPort.findById(fileId);
    }

    public Optional<StoredFile> getMetadataForUser(UserId viewerId, FileId fileId) {
        return fileMetadataPort.findById(fileId)
            .filter(file -> mayView(file, viewerId));
    }

    private boolean mayView(StoredFile file, UserId viewerId) {
        if (file.uploadedBy().equals(viewerId)) {
            return true;
        }
        return legacyMessageRepository.viewerMayAccessFileViaSharedNonE2eeMessage(file.id().value(), viewerId.value());
    }
}
